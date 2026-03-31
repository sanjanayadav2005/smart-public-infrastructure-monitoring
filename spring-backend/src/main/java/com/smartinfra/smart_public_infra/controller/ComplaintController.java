package com.smartinfra.smart_public_infra.controller;

import com.smartinfra.smart_public_infra.dto.ComplaintListResponse;
import com.smartinfra.smart_public_infra.dto.ComplaintResponse;
import com.smartinfra.smart_public_infra.dto.ReportIssueResponse;
import com.smartinfra.smart_public_infra.service.AdminAuthService;
import com.smartinfra.smart_public_infra.service.ComplaintService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final AdminAuthService adminAuthService;

    public ComplaintController(ComplaintService complaintService, AdminAuthService adminAuthService) {
        this.complaintService = complaintService;
        this.adminAuthService = adminAuthService;
    }

    @PostMapping(path = "/report-issue", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportIssueResponse> reportIssue(
            @RequestParam(name = "desc", required = false) String desc,
            @RequestParam(name = "loc", required = false) String loc,
            @RequestParam(name = "image", required = false) MultipartFile image) throws Exception {
        ComplaintResponse complaint = complaintService.reportIssue(desc, loc, image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ReportIssueResponse("Submitted successfully.", complaint));
    }

    @GetMapping("/complaints")
    public ResponseEntity<ComplaintListResponse> getComplaints(HttpSession session) {
        adminAuthService.requireAdmin(session);
        return ResponseEntity.ok(new ComplaintListResponse(complaintService.getAllComplaints()));
    }

    @PostMapping("/resolve/{complaintId}")
    public ResponseEntity<ReportIssueResponse> resolveComplaint(
            @PathVariable Long complaintId,
            HttpSession session) {
        adminAuthService.requireAdmin(session);
        ComplaintResponse complaint = complaintService.resolveComplaint(complaintId);
        return ResponseEntity.ok(new ReportIssueResponse("Complaint resolved.", complaint));
    }
}
