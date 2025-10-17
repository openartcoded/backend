package tech.artcoded.websitev2.mongodb.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.time.LocalDate;
import java.time.YearMonth;

@WritingConverter
public class YearMonthToLocalDateConverter implements Converter<YearMonth, LocalDate> {

    @Override
    public LocalDate convert(YearMonth yearMonth) {
        return yearMonth.atDay(1);
    }
}
