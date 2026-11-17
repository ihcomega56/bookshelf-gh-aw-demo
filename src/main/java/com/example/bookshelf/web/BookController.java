package com.example.bookshelf.web;

import com.example.bookshelf.service.BookshelfService;
import com.example.bookshelf.web.dto.BookRequest;
import com.example.bookshelf.web.dto.BookResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 蔵書に関する REST API。
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookshelfService bookshelfService;

    public BookController(BookshelfService bookshelfService) {
        this.bookshelfService = bookshelfService;
    }

    @GetMapping
    public List<BookResponse> list(@RequestParam(name = "keyword", required = false) String keyword) {
        return bookshelfService.searchBooks(keyword).stream()
                .map(BookResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable("id") Long id) {
        return BookResponse.from(bookshelfService.getBook(id));
    }

    @PostMapping
    public ResponseEntity<BookResponse> register(@Valid @RequestBody BookRequest request) {
        BookResponse response = BookResponse.from(bookshelfService.registerBook(
                request.title(), request.author(), request.isbn(), request.totalCopies()));
        return ResponseEntity.created(URI.create("/api/books/" + response.id())).body(response);
    }
}
