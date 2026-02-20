package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HomeController homeController;

    @Test
    void homeReturnsIndexView() {
        given(userRepository.count()).willReturn(5L);
        Model model = new ExtendedModelMap();

        String viewName = homeController.home(model);

        assertEquals("index", viewName);
        assertEquals(5L, model.asMap().get("userCount"));
        assertEquals("UP", model.asMap().get("status"));
    }

    @Test
    void homeAttributesArePopulated() {
        given(userRepository.count()).willReturn(0L);
        Model model = new ExtendedModelMap();

        homeController.home(model);

        assertEquals("BidMart Authentication Service", model.asMap().get("appName"));
        assertEquals(0L, model.asMap().get("userCount"));
    }
}
