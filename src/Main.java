import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        FastScanner scanner = new FastScanner();
        List<Long> numbers = new ArrayList<>();
        Long value;
        while ((value = scanner.nextLong()) != null) {
            numbers.add(value);
        }

        long[] input = new long[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            input[i] = numbers.get(i);
        }

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            TwoSetsService.Result result = context.getBean(TwoSetsService.class).findEqualDisjointSubsets(input);
            printSet(result.first());
            printSet(result.second());
        }
    }

    private static void printSet(List<Integer> set) {
        StringBuilder builder = new StringBuilder();
        builder.append(set.size()).append('\n');
        for (int i = 0; i < set.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(set.get(i));
        }
        builder.append('\n');
        System.out.print(builder);
    }

    @Configuration
    static class AppConfig {
        @Bean
        TwoSetsService twoSetsService() {
            return new TwoSetsService();
        }
    }

    static class FastScanner {
        private final BufferedInputStream input = new BufferedInputStream(System.in);

        Long nextLong() throws IOException {
            int ch;
            do {
                ch = input.read();
                if (ch == -1) {
                    return null;
                }
            } while (ch <= ' ');

            long sign = 1;
            if (ch == '-') {
                sign = -1;
                ch = input.read();
            }

            long value = 0;
            while (ch > ' ') {
                value = value * 10 + (ch - '0');
                ch = input.read();
            }
            return value * sign;
        }
    }
}
