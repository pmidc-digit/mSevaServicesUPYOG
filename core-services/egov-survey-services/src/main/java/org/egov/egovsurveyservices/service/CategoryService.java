package org.egov.egovsurveyservices.service;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.egovsurveyservices.config.ApplicationProperties;
import org.egov.egovsurveyservices.producer.Producer;
import org.egov.egovsurveyservices.repository.CategoryRepository;
import org.egov.egovsurveyservices.utils.ResponseInfoFactory;
import org.egov.egovsurveyservices.validators.CategoryValidator;
import org.egov.egovsurveyservices.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.*;

@Slf4j
@Service
public class CategoryService{

    @Autowired
    CategoryValidator categoryValidator;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ResponseInfoFactory factory;

    @Autowired
    private Producer producer;

    @Autowired
    private ApplicationProperties applicationProperties;

    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        RequestInfo requestInfo = categoryRequest.getRequestInfo();
        Map<String, String> pwdMap = new HashMap<>();
        categoryRequest.getCategories().forEach(category -> {
            enrichCreateRequest(category,requestInfo);
            categoryValidator.validateLabel(category);
            categoryValidator.validateTenantId(category);
            boolean categoryUnique = categoryValidator.isCategoryUnique(category.getLabel(), category.getTenantId());
            if(!categoryUnique){
                throw new CustomException("EG_SS_NO_UNIQUE_CATEGORY_FOR_TENANT","Category label not unique for tenantid.");
            }
        });
        producer.push(applicationProperties.getSaveCategoryTopic(),categoryRequest);
        return generateResponse(categoryRequest);
    }

    private void enrichCreateRequest(Category category, RequestInfo requestInfo) {

        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(requestInfo.getUserInfo().getUuid())
                .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                .createdTime(new Date().getTime())
                .lastModifiedTime(new Date().getTime())
                .build();
        category.setId(UUID.randomUUID().toString());
        category.setAuditDetails(auditDetails);
//        category.setIsActive(true);
    }

    private CategoryResponse generateResponse(CategoryRequest categoryRequest) {
        return CategoryResponse.builder()
                .responseInfo(factory.createResponseInfoFromRequestInfo(categoryRequest.getRequestInfo(), true))
                .categories(categoryRequest.getCategories()).build();
    }

    public CategoryResponse updateCategory(CategoryRequest categoryRequest) {
        categoryValidator.validateForUpdate(categoryRequest);
        Category category = categoryRequest.getCategories().get(0);
        List<Category> existingCategoryList = categoryRepository.getCategoryById(category.getId());
        Category existingCategoryFromDb = existingCategoryList.get(0);
        Gson gson = new Gson();
        Category deepCopy = gson.fromJson(gson.toJson(existingCategoryFromDb), Category.class);
        // Check if any updates are actually needed
        if(!Objects.nonNull(existingCategoryFromDb)){
            throw new CustomException("EG_SS_CATEGORY_NOT_FOUND", "category not found");
        }

        // Update only the allowed fields if they are present in the request
        if (category.getLabel() != null) {
            existingCategoryFromDb.setLabel(category.getLabel());
        }
        if (category.getIsActive() != null) {
            existingCategoryFromDb.setIsActive(category.getIsActive());
        }

        if (existingCategoryFromDb.equals(deepCopy)) {
            throw new CustomException("EG_SS_NOTHING_TO_UPDATE", "no content returned, nothing to update");
        }

        //check that same label for a tenant should not be existing
        Integer count = categoryRepository.fetchDuplicateCategoryCount(existingCategoryFromDb);

        if (count > 0) {
            throw new CustomException("EG_SS_CATEGORY_ALREADY_EXISTS","Category with this label and tenantId already exists, cannot update.");
        }

        // Update audit details
        String uuid = categoryRequest.getRequestInfo().getUserInfo().getUuid();
        existingCategoryFromDb.getAuditDetails().setLastModifiedBy(uuid);
        existingCategoryFromDb.getAuditDetails().setLastModifiedTime(System.currentTimeMillis());

        // Save the updated category
        categoryRequest.setCategories(Collections.singletonList(existingCategoryFromDb));
        producer.push(applicationProperties.getUpdateCategoryTopic(),categoryRequest);
        return generateResponse(categoryRequest);
    }

    public CategoryResponse searchCategory(@Valid CategorySearchCriteria criteria) {

        if(StringUtils.isBlank(criteria.getTenantId())) {
            throw new CustomException("EG_SS_TENANT_ID_REQUIRED_CATEGORY_SEARCH", "tenant id is mandatory.");
        }
        if (criteria.getPageNumber() < 1) {
            throw new IllegalArgumentException("Page number must be greater than or equal to 1");
        }

        CategoryRequest categoryRequest = new CategoryRequest();
        categoryRequest.setCategories(categoryRepository.fetchCategories(criteria));
        return generateResponse(categoryRequest);
    }

}
