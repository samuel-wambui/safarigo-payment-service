package Ngaritech.com.payment_service.MpesaIntergration.Config;

import Ngaritech.com.payment_service.common.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
@RequestMapping({"/api/mpesa-config", "/api/v1/mpesa/configs"})
@PreAuthorize("hasRole('ADMIN')")
public class MpesaConfigController {

    @Autowired
    private ConfigService configService;


    @PostMapping("/add")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> createMpesaConfig(@RequestBody MpesaConfigDto config) {
        MpesaConfigEntity createdConfig = configService.createMpesaConfig(config);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("M-Pesa configuration created", MpesaConfigResponse.from(createdConfig)));
    }

    @PutMapping("/update/{paybillNumber}")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> updateMpesaConfig(@PathVariable String paybillNumber, @RequestBody MpesaConfigDto updatedConfig) {
        MpesaConfigEntity updatedConfigResult = configService.updateMpesaConfig(paybillNumber, updatedConfig);
        return ResponseEntity.ok(ApiResponse.success("M-Pesa configuration updated", MpesaConfigResponse.from(updatedConfigResult)));
    }

    @DeleteMapping("/delete/{paybillNumber}")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> deleteMpesaConfig(@PathVariable String paybillNumber) {
        try {
            MpesaConfigEntity deletedConfig = configService.deleteMpesaConfig(paybillNumber);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success("Configuration deleted successfully", MpesaConfigResponse.from(deletedConfig)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(e.getMessage(), "NOT_FOUND", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure("Error while deleting configuration", "SERVER_ERROR", null));
        }
    }

    @PutMapping("/suspend/{paybillNumber}")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> suspendMpesaConfig(@PathVariable String paybillNumber) {
        try {
            MpesaConfigEntity suspendedConfig = configService.suspendMpesaConfig(paybillNumber);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success("Configuration suspended successfully", MpesaConfigResponse.from(suspendedConfig)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(e.getMessage(), "NOT_FOUND", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure("Error while suspending configuration", "SERVER_ERROR", null));
        }
    }

    @PutMapping("/unsuspend/{paybillNumber}")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> unSuspendMpesaConfig(@PathVariable String paybillNumber) {
        try {
            MpesaConfigEntity mpesaConfig = configService.unSuspendMpesaConfig(paybillNumber);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(ApiResponse.success("Configuration unsuspended successfully", MpesaConfigResponse.from(mpesaConfig)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure(e.getMessage(), "NOT_FOUND", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure("Error while unsuspending configuration", "SERVER_ERROR", null));
        }
    }


    @GetMapping("/get/{paymentPurpose}")
    public ResponseEntity<ApiResponse<MpesaConfigResponse>> getMpesaConfigByPaymentPurpose(@PathVariable String paymentPurpose) {
        MpesaConfigEntity config = configService.getConfigByPaymentPurpose(paymentPurpose);
        return ResponseEntity.ok(ApiResponse.success("M-Pesa configuration retrieved", MpesaConfigResponse.from(config)));
    }

    @GetMapping("/get/all")
    public ResponseEntity<ApiResponse<List<MpesaConfigResponse>>> getAllMpesaConfigs() {
        List<MpesaConfigEntity> configs = configService.getAllConfigs();
        return ResponseEntity.ok(ApiResponse.success(
                "M-Pesa configurations retrieved",
                configs.stream().map(MpesaConfigResponse::from).collect(Collectors.toList())
        ));
    }
}
