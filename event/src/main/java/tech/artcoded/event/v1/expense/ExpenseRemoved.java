package tech.artcoded.event.v1.expense;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpenseRemoved implements IExpenseEvent {
    private String expenseId;
    private List<String> uploadIds;

}
