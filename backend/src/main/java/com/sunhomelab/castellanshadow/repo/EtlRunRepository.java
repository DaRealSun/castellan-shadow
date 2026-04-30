package com.sunhomelab.castellanshadow.repo;

import com.sunhomelab.castellanshadow.domain.EtlRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtlRunRepository extends JpaRepository<EtlRun, Long> {
    List<EtlRun> findTop20ByJobNameOrderByStartedAtDesc(String jobName);
}
