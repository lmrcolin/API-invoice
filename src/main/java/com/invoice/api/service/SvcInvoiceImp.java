package com.invoice.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoInvoiceList;
import com.invoice.api.entity.CartItem;
import com.invoice.api.entity.Invoice;
import com.invoice.api.entity.InvoiceItem;
import com.invoice.api.entity.Item;
import com.invoice.api.repository.RepoCartItem;
import com.invoice.api.repository.RepoInvoice;
import com.invoice.api.repository.RepoItem;
import com.invoice.commons.mapper.MapperInvoice;
import com.invoice.commons.util.JwtDecoder;
import com.invoice.exception.ApiException;
import com.invoice.exception.DBAccessException;

@Service
public class SvcInvoiceImp implements SvcInvoice {

	private static final int CART_STATUS_ACTIVE = 1;
	private static final int CART_STATUS_INACTIVE = 0;
	private static final int ITEM_STATUS_ACTIVE = 1;
	private static final double TAX_RATE = 0.16d;
	private static final int INVOICE_STATUS_ACTIVE = 1;
	private static final int INVOICE_ITEM_STATUS_ACTIVE = 1;

	@Autowired
	private RepoInvoice repo;

	@Autowired
	private RepoCartItem repoCart;

	@Autowired
	private RepoItem repoItem;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	MapperInvoice mapper;

	@Override
	public List<DtoInvoiceList> findAll() {
		try {
			if (jwtDecoder.isAdmin()) {
				return mapper.toDtoList(repo.findAll());
			} else {
				Integer user_id = jwtDecoder.getUserId();
				return mapper.toDtoList(repo.findAllByUserId(user_id));
			}
		} catch (DataAccessException e) {
			throw new DBAccessException();
		}
	}

	@Override
	public Invoice findById(Integer id) {
		try {
			Invoice invoice = repo.findById(id).get();
			if (!jwtDecoder.isAdmin()) {
				Integer user_id = jwtDecoder.getUserId();
				if (invoice.getUser_id() != user_id) {
					throw new ApiException(HttpStatus.FORBIDDEN, "El token no es válido para consultar esta factura");
				}
			}
			return invoice;
		} catch (DataAccessException e) {
			throw new DBAccessException();
		} catch (NoSuchElementException e) {
			throw new ApiException(HttpStatus.NOT_FOUND, "El id de la factura no existe");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ApiResponse create() {
		try {
			Integer userId = jwtDecoder.getUserId();
			List<CartItem> cartItems = repoCart.findByUserIdAndStatus(userId, CART_STATUS_ACTIVE);
			if (cartItems.isEmpty()) {
				cartItems = repoCart.findByUserId(userId).stream()
						.filter(ci -> ci.getStatus() == null || ci.getStatus() == CART_STATUS_ACTIVE)
						.toList();
			}
			if (cartItems.isEmpty()) {
				throw new ApiException(HttpStatus.PRECONDITION_FAILED, "El carrito se encuentra vacío");
			}

			cartItems.forEach(ci -> {
				if (ci.getGtin() == null || ci.getGtin().isBlank()) {
					throw new ApiException(HttpStatus.PRECONDITION_FAILED,
							"Hay artículos en el carrito sin GTIN. Vuelve a agregarlos para completar la compra.");
				}
			});

			Map<String, Integer> quantityPerGtin = cartItems.stream()
					.collect(Collectors.groupingBy(CartItem::getGtin, Collectors.summingInt(CartItem::getQuantity)));

			List<Item> items = repoItem.findAllById(quantityPerGtin.keySet());
			Map<String, Item> itemsByGtin = items.stream()
					.collect(Collectors.toMap(Item::getGtin, Function.identity()));

			Set<String> missingGtins = new HashSet<>(quantityPerGtin.keySet());
			missingGtins.removeAll(itemsByGtin.keySet());
			if (!missingGtins.isEmpty()) {
				throw new ApiException(HttpStatus.NOT_FOUND,
						"No se encontraron los artículos: " + String.join(", ", missingGtins));
			}

			quantityPerGtin.forEach((gtin, quantity) -> {
				Item product = itemsByGtin.get(gtin);
				if (product.getStatus() != null && product.getStatus() != ITEM_STATUS_ACTIVE) {
					throw new ApiException(HttpStatus.PRECONDITION_FAILED,
							"El artículo " + gtin + " no está disponible");
				}
				if (product.getStock() == null || product.getStock() < quantity) {
					throw new ApiException(HttpStatus.PRECONDITION_FAILED,
							"No hay stock suficiente para el artículo " + gtin);
				}
				if (product.getUnitPrice() == null) {
					throw new ApiException(HttpStatus.PRECONDITION_FAILED,
							"El artículo " + gtin + " no tiene precio configurado");
				}
			});

			List<InvoiceItem> invoiceItems = new ArrayList<>();
			BigDecimal total = BigDecimal.ZERO;
			BigDecimal taxes = BigDecimal.ZERO;
			BigDecimal subtotal = BigDecimal.ZERO;

			for (CartItem cartItem : cartItems) {
				Item product = itemsByGtin.get(cartItem.getGtin());
				BigDecimal unitPrice = BigDecimal.valueOf(product.getUnitPrice());
				BigDecimal quantity = BigDecimal.valueOf(cartItem.getQuantity());
				BigDecimal lineTotal = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
				BigDecimal lineTaxes = lineTotal.multiply(BigDecimal.valueOf(TAX_RATE)).setScale(2,
						RoundingMode.HALF_UP);
				BigDecimal lineSubtotal = lineTotal.subtract(lineTaxes).setScale(2, RoundingMode.HALF_UP);

				total = total.add(lineTotal);
				taxes = taxes.add(lineTaxes);
				subtotal = subtotal.add(lineSubtotal);

				InvoiceItem invoiceItem = new InvoiceItem();
				invoiceItem.setGtin(cartItem.getGtin());
				invoiceItem.setQuantity(cartItem.getQuantity());
				invoiceItem.setUnit_price(unitPrice.doubleValue());
				invoiceItem.setTotal(lineTotal.doubleValue());
				invoiceItem.setTaxes(lineTaxes.doubleValue());
				invoiceItem.setSubtotal(lineSubtotal.doubleValue());
				invoiceItem.setStatus(INVOICE_ITEM_STATUS_ACTIVE);
				invoiceItems.add(invoiceItem);
			}

			Invoice invoice = new Invoice();
			invoice.setUser_id(userId);
			invoice.setCreated_at(LocalDate.now());
			invoice.setSubtotal(subtotal.doubleValue());
			invoice.setTaxes(taxes.doubleValue());
			invoice.setTotal(total.doubleValue());
			invoice.setStatus(INVOICE_STATUS_ACTIVE);
			invoice.setItems(invoiceItems);

			repo.save(invoice);

			itemsByGtin.forEach((gtin, product) -> {
				Integer remaining = product.getStock() - quantityPerGtin.get(gtin);
				product.setStock(remaining);
			});
			repoItem.saveAll(itemsByGtin.values());

			cartItems.forEach(item -> item.setStatus(CART_STATUS_INACTIVE));
			repoCart.saveAll(cartItems);

			return new ApiResponse("La factura ha sido registrada");
		} catch (DataAccessException e) {
			throw new DBAccessException();
		}
	}
}
