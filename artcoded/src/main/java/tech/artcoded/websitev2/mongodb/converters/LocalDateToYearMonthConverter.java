package tech.artcoded.websitev2.mongodb.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.time.LocalDate;
import java.time.YearMonth;

@ReadingConverter
public class LocalDateToYearMonthConverter implements Converter<LocalDate, YearMonth> {

    @Override
    public YearMonth convert(LocalDate localDate) {
        return YearMonth.from(localDate);
    }
}
