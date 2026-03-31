package com.smartinfra.smart_public_infra.service;

import com.smartinfra.smart_public_infra.config.AppStorageProperties;
import com.smartinfra.smart_public_infra.dto.ComplaintResponse;
import com.smartinfra.smart_public_infra.exception.BadRequestException;
import com.smartinfra.smart_public_infra.exception.ResourceNotFoundException;
import com.smartinfra.smart_public_infra.model.Complaint;
import com.smartinfra.smart_public_infra.model.ComplaintStatus;
import com.smartinfra.smart_public_infra.repository.ComplaintRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final Path uploadDirectory;

    public ComplaintService(ComplaintRepository complaintRepository, AppStorageProperties storageProperties)
            throws IOException {
        this.complaintRepository = complaintRepository;
        this.uploadDirectory = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDirectory);
    }

    public ComplaintResponse reportIssue(String desc, String loc, MultipartFile image) throws IOException {
        String description = desc == null ? "" : desc.trim();
        String location = loc == null ? "" : loc.trim();

        if (description.isBlank() || location.isBlank() || image == null || image.isEmpty()) {
            throw new BadRequestException("Description, location, and image are required.");
        }

        String storedFilename = storeImage(image);

        Complaint complaint = new Complaint();
        complaint.setDescription(description);
        complaint.setLocation(location);
        complaint.setImage(storedFilename);
        complaint.setStatus(ComplaintStatus.Pending);

        return ComplaintResponse.fromEntity(complaintRepository.save(complaint));
    }

    public List<ComplaintResponse> getAllComplaints() {
        return complaintRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(ComplaintResponse::fromEntity)
                .toList();
    }

    public ComplaintResponse resolveComplaint(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found."));

        complaint.setStatus(ComplaintStatus.Resolved);
        return ComplaintResponse.fromEntity(complaintRepository.save(complaint));
    }

    private String storeImage(MultipartFile image) throws IOException {
        String originalFilename = image.getOriginalFilename();
        String safeFilename = buildSafeFilename(originalFilename == null ? "upload" : originalFilename);
        Path destination = uploadDirectory.resolve(safeFilename).normalize();

        if (!destination.startsWith(uploadDirectory)) {
            throw new BadRequestException("Invalid file name.");
        }

        Files.copy(image.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return safeFilename;
    }

    private String buildSafeFilename(String originalFilename) {
        String cleaned = Paths.get(originalFilename).getFileName().toString()
                .replaceAll("[^A-Za-z0-9._-]", "_");

        int dotIndex = cleaned.lastIndexOf('.');
        String extension = dotIndex >= 0 ? cleaned.substring(dotIndex) : "";
        String baseName = dotIndex >= 0 ? cleaned.substring(0, dotIndex) : cleaned;

        if (baseName.isBlank()) {
            baseName = "upload";
        }

        return baseName + "_" + UUID.randomUUID() + extension;
    }
}
