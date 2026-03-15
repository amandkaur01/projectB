package com.example.iotlab.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iotlab.model.Borrow;
import com.example.iotlab.repository.BorrowRepository;
import com.example.iotlab.service.BorrowService;

@RestController
@RequestMapping("/borrow")
@CrossOrigin
public class BorrowController {

    @Autowired
    private BorrowService service;
    @Autowired
    private BorrowRepository borrowRepository;

    @PostMapping
    public Borrow borrowEquipment(@RequestBody Borrow borrow) {
        return service.borrowEquipment(borrow);
    }

    @PutMapping("/return/{id}/{qty}")
    public Borrow returnEquipment(@PathVariable Long id, @PathVariable int qty) {
        return service.returnEquipment(id, qty);
    }

    @GetMapping
    public List<Borrow> getAllBorrowed() {
        return service.getAllBorrowed();
    }

    // ── NEW: Fetch a single borrow record by ID ────────────────────────
    // Used by ReturnPage to show equipment name, borrowed qty and remaining qty
    @GetMapping("/{id}")
    public ResponseEntity<Borrow> getBorrowById(@PathVariable Long id) {
        return borrowRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/student/{studentName}")
    public List<Borrow> getBorrowsByStudent(@PathVariable String studentName) {
        return service.getBorrowsByStudent(studentName);
    }

    @GetMapping("/student/analytics/{studentName}")
    public Map<String, Long> studentAnalytics(@PathVariable String studentName) {
        return service.getStudentAnalytics(studentName);
    }
}
