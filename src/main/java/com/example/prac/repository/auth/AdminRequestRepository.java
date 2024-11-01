package com.example.prac.repository.auth;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import com.example.prac.model.auth.AdminRequest;
import com.example.prac.model.auth.User;

@Repository
public interface AdminRequestRepository extends CrudRepository<AdminRequest, Long>,
        PagingAndSortingRepository<AdminRequest, Long> {
    Optional<AdminRequest> findByRequester(User requester);
}