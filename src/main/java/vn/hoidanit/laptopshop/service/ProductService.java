package vn.hoidanit.laptopshop.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import vn.hoidanit.laptopshop.domain.Cart;
import vn.hoidanit.laptopshop.domain.Order;
import vn.hoidanit.laptopshop.domain.OrderDetail;
import vn.hoidanit.laptopshop.domain.CartDetail;
import vn.hoidanit.laptopshop.domain.Product;
import vn.hoidanit.laptopshop.domain.User;
import vn.hoidanit.laptopshop.repository.CartDetailRepository;
import vn.hoidanit.laptopshop.repository.CartRepository;
import vn.hoidanit.laptopshop.repository.OrderDetailRepository;
import vn.hoidanit.laptopshop.repository.OrderRepository;
import vn.hoidanit.laptopshop.repository.ProductRepository;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartDetailRepository cartDetailRepository;
    private final UserService userService;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    public ProductService(ProductRepository productRepository, CartRepository cartRepository,
            CartDetailRepository cartDetailRepository, UserService userService, OrderRepository orderRepository,
            OrderDetailRepository orderDetailRepository) {
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.cartDetailRepository = cartDetailRepository;
        this.userService = userService;
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
    }

    public Product createProduct(Product pr) {
        return this.productRepository.save(pr);
    }

    public Page<Product> fetchProducts(Pageable page) {
        return this.productRepository.findAll(page);
    }

    public void deleteProduct(long id) {
        this.productRepository.deleteById(id);
    }

    // public Product getProductById(long id) {
    // return this.productRepository.findById(id);
    // }

    public Optional<Product> fetchProductById(long id) {
        return this.productRepository.findById(id);
    }

    public void handleAddProductToCart(String email, long productId, HttpSession session, long quantity) {
        User user = this.userService.getUserByEmail(email);
        if (user != null) {
            // check user đã có cart hay chưa ? nếu chưa - tạo mới
            Cart cart = this.cartRepository.findByUser(user);

            if (cart == null) {
                // create new car
                Cart otherCart = new Cart();
                otherCart.setUser(user);
                otherCart.setSum(0);
                cart = this.cartRepository.save(otherCart);
            }
            // save cart_detail
            // tim product by id
            Optional<Product> producOptional = this.productRepository.findById(productId);
            if (producOptional.isPresent()) {
                Product realProduct = producOptional.get();

                // check san pham da tung duoc them vao gio hang hay chua
                CartDetail oldDetail = this.cartDetailRepository.findByCartAndProduct(cart, realProduct);
                if (oldDetail == null) {
                    CartDetail cd = new CartDetail();
                    cd.setCart(cart);
                    cd.setProduct(realProduct);
                    cd.setPrice(realProduct.getPrice());
                    cd.setQuantity(quantity);

                    int sum = cart.getSum() + 1;
                    cart.setSum(sum);
                    this.cartDetailRepository.save(cd);
                    // update cart(sum)

                    this.cartRepository.save(cart);

                    session.setAttribute("sum", sum);
                } else {
                    oldDetail.setQuantity(oldDetail.getQuantity() + quantity);
                    this.cartDetailRepository.save(oldDetail);
                }

            }

        }

    }

    public Cart fetchByUser(User user) {
        return this.cartRepository.findByUser(user);
    }

    public void handleRemoveCartDetail(long cartDetailId, HttpSession session) {
        Optional<CartDetail> cartDetailOptional = this.cartDetailRepository.findById(cartDetailId);
        if (cartDetailOptional.isPresent()) {
            CartDetail cartDetail = cartDetailOptional.get();

            Cart currentCart = cartDetail.getCart();
            // delete cart-detail
            this.cartDetailRepository.deleteById(cartDetailId);

            // update cart
            if (currentCart.getSum() > 1) {
                // update current cart
                int s = currentCart.getSum() - 1;
                currentCart.setSum(s);
                session.setAttribute("sum", s);
                this.cartRepository.save(currentCart);
            } else {
                // delete cart (sum = 1)
                this.cartRepository.deleteById(currentCart.getId());
                session.setAttribute("sum", 0);
            }
        }
    }

    public void handleUpdateCartBeforeCheckout(List<CartDetail> cartDetails) {
        for (CartDetail cartDetail : cartDetails) {
            Optional<CartDetail> cdOptional = this.cartDetailRepository.findById(cartDetail.getId());
            if (cdOptional.isPresent()) {
                CartDetail currentCartDetail = cdOptional.get();
                currentCartDetail.setQuantity(cartDetail.getQuantity());
                this.cartDetailRepository.save(currentCartDetail);
            }
        }
    }

    public void handlePlaceOrder(User user, HttpSession session, String receiverName, String receiverAddress,
            String receiverPhone) {

        // step 1: get cart by user
        Cart cart = this.cartRepository.findByUser(user);
        if (cart != null) {
            List<CartDetail> cartDetails = cart.getCartDetails();

            if (cartDetails != null) {

                // create order
                Order order = new Order();
                order.setUser(user);
                order.setReceiverName(receiverName);
                order.setReceiverAddress(receiverAddress);
                order.setReceiverPhone(receiverPhone);
                order.setStatus("PENDING");

                double sum = 0;
                for (CartDetail cd : cartDetails) {
                    sum += cd.getPrice();
                }
                order.setTotalPrice(sum);
                order = this.orderRepository.save(order);

                // create orderDetail

                for (CartDetail cd : cartDetails) {
                    OrderDetail orderDetail = new OrderDetail();
                    orderDetail.setOrder(order);
                    orderDetail.setProduct(cd.getProduct());
                    orderDetail.setPrice(cd.getPrice());
                    orderDetail.setQuantity(cd.getQuantity());

                    this.orderDetailRepository.save(orderDetail);
                }

                // step 2: delete cart_detail and cart
                for (CartDetail cd : cartDetails) {
                    this.cartDetailRepository.deleteById(cd.getId());
                }

                this.cartRepository.deleteById(cart.getId());

                // step 3 : update session
                session.setAttribute("sum", 0);
            }
        }
    }
}
