package com.hotel.dto;

import java.time.LocalDate;
import java.util.List;

public class ReservationRequest {
    private Integer userId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private List<Integer> roomIds;

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
    public List<Integer> getRoomIds() { return roomIds; }
    public void setRoomIds(List<Integer> roomIds) { this.roomIds = roomIds; }
}

