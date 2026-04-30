package com.sunhomelab.castellanshadow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "etl_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtlRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "rows_in")
    private Integer rowsIn;

    @Column(name = "rows_out")
    private Integer rowsOut;

    @Column(name = "error_msg", length = 2000)
    private String errorMsg;
}
