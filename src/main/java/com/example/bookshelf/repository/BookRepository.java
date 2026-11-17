package com.example.bookshelf.repository;

import com.example.bookshelf.domain.Book;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByIsbn(String isbn);

    // TODO: 蔵書が増えるとレスポンスが肥大化するため、Pageable を受け取る検索に置き換える
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
            String titleKeyword, String authorKeyword);
}
