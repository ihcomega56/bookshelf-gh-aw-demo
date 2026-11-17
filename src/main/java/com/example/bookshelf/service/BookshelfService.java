package com.example.bookshelf.service;

import com.example.bookshelf.domain.Book;
import com.example.bookshelf.domain.Loan;
import com.example.bookshelf.repository.BookRepository;
import com.example.bookshelf.repository.LoanRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 蔵書と貸出に関するユースケースをまとめたサービス。
 */
@Service
@Transactional
public class BookshelfService {

    /** 1 人あたりの貸出上限（冊数）。 */
    private static final int MAX_BORROW_COUNT_PER_BORROWER = 5;

    /** 貸出期間（日数）。 */
    private static final int LOAN_PERIOD_DAYS = 14;

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    public BookshelfService(BookRepository bookRepository, LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
    }

    @Transactional(readOnly = true)
    public List<Book> findAllBooks() {
        return bookRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Book> searchBooks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return bookRepository.findAll();
        }
        return bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(keyword, keyword);
    }

    @Transactional(readOnly = true)
    public Book getBook(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("蔵書が見つかりません: id=" + id));
    }

    public Book registerBook(String title, String author, String isbn, int totalCopies) {
        bookRepository.findByIsbn(isbn).ifPresent(existing -> {
            throw new BookshelfException("同じ ISBN の蔵書が既に登録されています: " + isbn);
        });
        return bookRepository.save(new Book(title, author, isbn, totalCopies));
    }

    /**
     * 蔵書を貸し出す。
     *
     * <p>TODO: 予約機能（在庫切れの場合に順番待ちへ登録する）が未実装。</p>
     */
    public Loan borrow(Long bookId, String borrower) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new NoSuchElementException("蔵書が見つかりません: id=" + bookId));

        int lent = loanRepository.findByBookIdAndReturnedOnIsNull(bookId).size();
        if (book.getTotalCopies() - lent <= 0) {
            throw new BookshelfException("貸出可能な在庫がありません: " + book.getTitle());
        }

        List<Loan> borrowerLoans = loanRepository.findByBorrowerAndReturnedOnIsNull(borrower);
        if (borrowerLoans.size() >= MAX_BORROW_COUNT_PER_BORROWER) {
            throw new BookshelfException("貸出上限（" + MAX_BORROW_COUNT_PER_BORROWER + " 冊）に達しています: " + borrower);
        }
        LocalDate today = LocalDate.now();
        for (Loan loan : borrowerLoans) {
            if (loan.getBookId().equals(bookId)) {
                throw new BookshelfException("同じ蔵書を重複して借りることはできません: " + book.getTitle());
            }
            // 延滞している本がある場合は新規貸出を認めない
            if (loan.getDueOn().isBefore(today)) {
                throw new BookshelfException("延滞中の蔵書があるため貸し出せません: " + borrower);
            }
        }

        return loanRepository.save(new Loan(bookId, borrower, today, today.plusDays(LOAN_PERIOD_DAYS)));
    }

    public Loan giveBack(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NoSuchElementException("貸出記録が見つかりません: id=" + loanId));
        if (loan.isReturned()) {
            throw new BookshelfException("既に返却済みです: id=" + loanId);
        }
        loan.markReturned(LocalDate.now());
        return loanRepository.save(loan);
    }

    public Loan renew(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NoSuchElementException("貸出記録が見つかりません: id=" + loanId));
        if (loan.isReturned()) {
            throw new BookshelfException("既に返却済みです: id=" + loanId);
        }
        if (loan.getDueOn().isBefore(LocalDate.now())) {
            throw new BookshelfException("延滞中の貸出は更新できません: id=" + loanId);
        }
        loan.renew(LOAN_PERIOD_DAYS);
        return loanRepository.save(loan);
    }

    @Transactional(readOnly = true)
    public List<Loan> findOverdueLoans() {
        LocalDate today = LocalDate.now();
        return loanRepository.findByReturnedOnIsNull().stream()
                .filter(loan -> loan.getDueOn().isBefore(today))
                .toList();
    }

    // TODO: 延滞者へのリマインドメール送信は未実装（通知基盤の選定待ち）
}
