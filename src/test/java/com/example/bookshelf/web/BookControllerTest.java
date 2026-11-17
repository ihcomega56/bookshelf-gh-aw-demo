package com.example.bookshelf.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bookshelf.domain.Book;
import com.example.bookshelf.domain.Loan;
import com.example.bookshelf.repository.BookRepository;
import com.example.bookshelf.repository.LoanRepository;
import com.example.bookshelf.service.BookshelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 蔵書 API の疎通テスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookshelfService bookshelfService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    private Book book;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        bookRepository.deleteAll();
        book = bookRepository.save(new Book("テスト本", "著者", "9784798121963", 1));
    }

    @Test
    void 蔵書一覧を取得できる() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void 著者名の部分一致で蔵書を検索できる() throws Exception {
        mockMvc.perform(get("/api/books").param("keyword", "著"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(book.getId()));
    }

    @Test
    void ISBNが不正な場合は400を返す() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"テスト本","author":"著者","isbn":"123","totalCopies":1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 貸出期限を更新できる() throws Exception {
        Loan loan = bookshelfService.borrow(book.getId(), "alice");

        mockMvc.perform(post("/api/loans/{loanId}/renew", loan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renewalCount").value(1))
                .andExpect(jsonPath("$.dueOn").value(loan.getDueOn().plusDays(14).toString()));
    }

    @Test
    void 返却済みの貸出更新は409を返す() throws Exception {
        Loan loan = bookshelfService.borrow(book.getId(), "alice");
        bookshelfService.giveBack(loan.getId());

        mockMvc.perform(post("/api/loans/{loanId}/renew", loan.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("返却済み")));
    }

    @Test
    void 存在しない貸出更新は404を返す() throws Exception {
        mockMvc.perform(post("/api/loans/{loanId}/renew", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("貸出記録が見つかりません")));
    }
}
