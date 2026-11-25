package com.example.pyro_cryo.utils.webhooks.repository;

import com.example.pyro_cryo.utils.webhooks.entity.EventMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventMessageRepository extends JpaRepository<EventMessage, String> {

    List<EventMessage> findByIsSent(boolean isSent);
}
