package com.bankflow.transfer.controller;

import com.bankflow.common.dto.PageResponse;
import com.bankflow.common.exception.TransferNotFoundException;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
public class TransferControllerTest {

    @MockitoBean
    private TransferService transferService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getTransferHistory_shouldReturn400_whenPageIsNegative() throws Exception {
        mockMvc.perform(
                        get("/transfers")
                                .param("page", "-1")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));

        verifyNoInteractions(transferService);
    }

    @Test
    void getTransferById_shouldReturn404_whenTransferDoesNotExist() throws Exception {
        when(transferService.findTransferById(999L))
                .thenThrow(new TransferNotFoundException(999L));

        mockMvc.perform(
                        get("/transfers/{id}", 999L)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    @Test
    void createTransfer_shouldReturn400_whenAmountIsNegative() throws Exception {
        mockMvc.perform(
                        post("/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fromAccountId": 1,
                                          "toAccountId": 2,
                                          "amount": -100,
                                          "currency": "VND"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.amount")
                        .value("Amount must be greater than zero"));
    }

    @Test
    void getTransferHistory_shouldReturn200_withPaginationResponse() throws Exception {
        TransferResponse transfer = new TransferResponse(
                10L,
                1L,
                2L,
                new BigDecimal("500000.00"),
                Currency.VND,
                TransferStatus.COMPLETED,
                LocalDateTime.of(2026, 10, 3, 17, 9, 33)
        );

        PageResponse<TransferResponse> response = new PageResponse<>(
                List.of(transfer),
                0,
                20,
                1,
                1,
                true,
                true
        );

        when(transferService.getTransferHistory(
                0,
                20,
                null,
                null,
                null,
                null
        )).thenReturn(response);

        mockMvc.perform(
                        get("/transfers")
                                .param("page", "0")
                                .param("size", "20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].fromAccountId").value(1))
                .andExpect(jsonPath("$.content[0].toAccountId").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(500000.00))
                .andExpect(jsonPath("$.content[0].currency").value("VND"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void createTransfer_shouldReturn201_whenRequestIsValid() throws Exception {
        TransferResponse response = new TransferResponse(
                10L,
                1L,
                2L,
                new BigDecimal("500000.00"),
                Currency.VND,
                TransferStatus.COMPLETED,
                LocalDateTime.of(2026, 10, 5, 19, 30)
        );

        when(transferService.createTransfer(any(CreateTransferRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fromAccountId": 1,
                                          "toAccountId": 2,
                                          "amount": 500000.00,
                                          "currency": "VND"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fromAccountId").value(1))
                .andExpect(jsonPath("$.toAccountId").value(2))
                .andExpect(jsonPath("$.amount").value(500000.00))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

}
