package by.st.monitoringandanalyzingexchangerates.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class BankApiService {
    List<String> getBank() {
        return Arrays.asList("Нацбанк РБ", "Альфа-Банк", "Беларусбанк");
    }
}
