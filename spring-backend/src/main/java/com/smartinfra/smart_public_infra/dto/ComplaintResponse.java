package com.smartinfra.smart_public_infra.dto;

import com.smartinfra.smart_public_infra.model.Complaint;

public record ComplaintResponse(
        Long id,
        String description,
        String location,
        String image,
        String status
) {
    public static ComplaintResponse fromEntity(Complaint complaint) {
        String imageUrl = complaint.getImage() == null || complaint.getImage().isBlank()
                ? null
                : "/static/uploads/" + complaint.getImage();

        return new ComplaintResponse(
                complaint.getId(),
                complaint.getDescription(),
                complaint.getLocation(),
                imageUrl,
                complaint.getStatus().name()
        );
    }
}
