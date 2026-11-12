package com.codeguard.ai;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "codeguard.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAIReviewProvider implements AIReviewProvider {
    public String name() { return "mock"; }
    public boolean external() { return false; }
    public BigDecimal reservation(Input input) { return BigDecimal.ZERO; }
    public Output review(Input input) {
        return new Output("Mock provider: " + input.files().size() + " file(s) processed locally. No cloud AI analysis was performed. Static heuristic findings are shown separately; review the changes manually.", List.of(), new Usage("mock", 0, 0, false, BigDecimal.ZERO));
    }
}
