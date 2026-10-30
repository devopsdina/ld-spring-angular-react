package com.launchdarkly.sample.common;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** The five demo products, held in memory. */
public class ProductCatalog {

	private static final List<Product> PRODUCTS = List.of(
			new Product(1, "Chair", "https://images.pexels.com/photos/7193570/pexels-photo-7193570.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2", new BigDecimal("90.00"), "furniture"),
			new Product(2, "Camera", "http://ecx.images-amazon.com/images/I/61%2BABMMN5zL._SL1500_.jpg", new BigDecimal("600.00"), "electronics"),
			new Product(3, "Shoes", "https://images.pexels.com/photos/6050919/pexels-photo-6050919.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2", new BigDecimal("130.00"), "clothing"),
			new Product(4, "Soda", "https://images.pexels.com/photos/4113627/pexels-photo-4113627.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=2", new BigDecimal("6.00"), "food"),
			new Product(5, "Utensils", "https://images.pexels.com/photos/4397817/pexels-photo-4397817.jpeg", new BigDecimal("40.00"), "housewares"));

	public Optional<Product> findById(int id) {
		return PRODUCTS.stream().filter(product -> product.id() == id).findFirst();
	}

}
