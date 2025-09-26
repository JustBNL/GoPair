package com.gopair.roomservice.service;

public interface JoinResultQueryService {
    class JoinStatusVO {
        public enum Status { JOINED, PROCESSING, FAILED }
        public Status status;
        public Long roomId;
        public Long userId;
        public String message;
        public JoinStatusVO(Status status, Long roomId, Long userId, String message) {
            this.status = status;
            this.roomId = roomId;
            this.userId = userId;
            this.message = message;
        }
    }

    JoinStatusVO queryByToken(String joinToken);
} 