package com.example.pyro_cryo.utils.webhooks.repository;

import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventMessageRepository extends JpaRepository<EventMessage, String> {
}
