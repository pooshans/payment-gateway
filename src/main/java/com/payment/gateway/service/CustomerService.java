package com.payment.gateway.service;

import com.payment.gateway.dto.CustomerDto;
import com.payment.gateway.entity.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerService {

    // Create a new customer
    CustomerDto createCustomer(CustomerDto customerDto);

    // Update an existing customer
    CustomerDto updateCustomer(Long id, CustomerDto customerDto);

    // Get customer by ID
    Optional<CustomerDto> getCustomer(Long id);

    // Get customer by email
    Optional<CustomerDto> getCustomerByEmail(String email);

    // Get all customers
    List<CustomerDto> getAllCustomers();

    // Delete a customer
    void deleteCustomer(Long id);

    // For internal use
    Optional<Customer> findById(Long id);

    Optional<Customer> findByEmail(String email);

    Customer saveCustomer(Customer customer);
}
