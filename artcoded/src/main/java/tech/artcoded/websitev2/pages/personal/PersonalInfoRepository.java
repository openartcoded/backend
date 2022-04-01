package tech.artcoded.websitev2.pages.personal;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PersonalInfoRepository extends MongoRepository<PersonalInfo, String> {
}
