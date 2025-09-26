package com.gopair.roomservice.service;

public interface JoinReservationService {

    enum ReserveStatus {
        ACCEPTED, ALREADY_JOINED, FULL, CLOSED, EXPIRED, PROCESSING
    }

    class PreReserveResult {
        public ReserveStatus status;
        public String joinToken;
        public String message;
        public PreReserveResult(ReserveStatus status, String joinToken, String message) {
            this.status = status;
            this.joinToken = joinToken;
            this.message = message;
        }
        public static PreReserveResult of(ReserveStatus status, String token, String msg){
            return new PreReserveResult(status, token, msg);
        }
    }

    PreReserveResult preReserve(Long roomId, Long userId, String displayName);
} 