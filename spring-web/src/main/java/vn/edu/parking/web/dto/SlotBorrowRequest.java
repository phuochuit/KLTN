package vn.edu.parking.web.dto;

public record SlotBorrowRequest(String borrowedPlate, Integer hours, String borrowNotes) { }
