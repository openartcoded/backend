package tech.artcoded.websitev2.sms;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Sms {
    private String phoneNumber;
    private String message;

    @JsonIgnore
    public String getCleanPhoneNumber() {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.replaceAll(" ", "").replaceAll("\\(.\\)|/| |\r|\n|\t|\\.", "");
    }
}
