package com.example.demo.service;

import com.example.demo.dto.OrderDetailDTO;
import com.example.demo.entity.Order.OrderDetail;
import com.example.demo.repository.OrderDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderDetailService {

    private final OrderDetailRepository orderDetailRepository;

    public List<OrderDetailDTO> getOrderDetailsByOrderId(Long orderId) {
        List<OrderDetail> details = orderDetailRepository.findByOrderHeaderId(orderId);

        // Code đã được thu gọn tối đa nhờ Constructor của OrderDetailDTO
        return details.stream()
                .map(OrderDetailDTO::new) // Tương đương: .map(detail -> new OrderDetailDTO(detail))
                .collect(Collectors.toList());
    }
}