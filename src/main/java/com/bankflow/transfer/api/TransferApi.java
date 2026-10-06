package com.bankflow.transfer.api;

import com.bankflow.common.dto.PageResponse;
import com.bankflow.common.exception.ErrorResponse;
import com.bankflow.transfer.dto.CreateTransferRequest;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.enums.TransferStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

public interface TransferApi {

    @Operation(
            summary = "Get transfer history",
            description = "Returns paginated transfer history with optional filters by status, account and created time range."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer history retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid pagination or filter parameters",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    PageResponse<TransferResponse> getTransferHistory(

            @Parameter(
                    description = "Zero-based page number",
                    example = "0"
            )
            @Min(0)
            int page,

            @Parameter(
                    description = "Number of items per page, from 1 to 100",
                    example = "20"
            )
            @Min(1)
            @Max(100)
            int size,

            @Parameter(
                    description = "Filter transfers by transfer status"
            )
            TransferStatus status,

            @Parameter(
                    description = "Filter transfers where the account is either the sender or receiver",
                    example = "1"
            )
            Long accountId,

            @Parameter(
                    description = "Filter transfers created at or after this time",
                    example = "2026-10-01T00:00:00"
            )
            LocalDateTime from,

            @Parameter(
                    description = "Filter transfers created at or before this time",
                    example = "2026-10-05T23:59:59"
            )
            LocalDateTime to
    );

    @Operation(
            summary = "Create transfer",
            description = "Creates a transfer between two accounts and returns the completed transfer."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Transfer created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transfer request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Source or destination account not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Transfer conflict such as insufficient funds or inactive account",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<TransferResponse> createTransfer(
            @RequestBody(
                    description = "Transfer creation request",
                    required = true
            )
            @Valid
            CreateTransferRequest request
    );

    @Operation(
            summary = "Get transfer by id",
            description = "Returns a transfer by its unique identifier."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfer retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Transfer not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<TransferResponse> getTransferById(
            @Parameter(
                    description = "Transfer identifier",
                    example = "10"
            )
            Long id
    );
}
