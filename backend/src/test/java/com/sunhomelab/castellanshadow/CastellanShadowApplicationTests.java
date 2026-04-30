package com.sunhomelab.castellanshadow;

import com.sunhomelab.castellanshadow.repo.EtfRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class CastellanShadowApplicationTests {

    @Autowired
    EtfRepository etfRepository;

    @Test
    void contextLoadsAndSeedsCastellanFunds() {
        assertThat(etfRepository.findByTicker("CTEF")).isPresent();
        assertThat(etfRepository.findByTicker("CTIF")).isPresent();
        assertThat(etfRepository.findByCastellanFundTrue()).hasSize(2);
    }
}
