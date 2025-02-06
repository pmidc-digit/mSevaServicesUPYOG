package org.egov.egovsurveyservices.service;

import com.google.gson.Gson;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.egovsurveyservices.config.ApplicationProperties;
import org.egov.egovsurveyservices.producer.Producer;
import org.egov.egovsurveyservices.repository.QuestionRepository;
import org.egov.egovsurveyservices.validators.QuestionValidator;
import org.egov.egovsurveyservices.web.models.*;
import org.egov.egovsurveyservices.web.models.enums.Status;
import org.egov.egovsurveyservices.web.models.enums.Type;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {
    @InjectMocks
    private QuestionService questionService;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private Producer producer;

    @Mock
    private QuestionValidator questionValidator;

    private RequestInfo requestInfo;
    private Gson gson;

    @BeforeEach
    void setUp() {
        requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("1").build())
                .build();
        gson = new Gson();
    }

    @Test
    public void testCreateQuestionSuccess() {
        when(applicationProperties.getMaxCreateLimit()).thenReturn(5);
        Question question = Question.builder()
                .questionStatement("Test Question")
                .build();
        List<Question> questions = Collections.singletonList(question);
        QuestionRequest questionRequest = QuestionRequest.builder()
                .requestInfo(requestInfo)
                .questions(questions)
                .build();
        when(applicationProperties.getSaveQuestionTopic()).thenReturn("test-topic");
        QuestionResponse response = questionService.createQuestion(questionRequest);

        assertEquals("1", response.getQuestions().get(0).getAuditDetails().getCreatedBy());
        assertEquals("1", response.getQuestions().get(0).getAuditDetails().getLastModifiedBy());
        assertNotNull(response.getQuestions().get(0).getUuid());
        assertEquals(Status.ACTIVE, response.getQuestions().get(0).getStatus());
        assertEquals(Collections.singletonList("NA"), response.getQuestions().get(0).getOptions());
    }

    @Test
    public void testCreateQuestionWithOptions() {
        when(applicationProperties.getMaxCreateLimit()).thenReturn(5);
        Question question = Question.builder()
                .questionStatement("Test Question")
                .options(Arrays.asList("Option 1", "Option 2"))
                .build();
        List<Question> questions = Collections.singletonList(question);
        QuestionRequest questionRequest = QuestionRequest.builder()
                .requestInfo(requestInfo)
                .questions(questions)
                .build();

        when(applicationProperties.getSaveQuestionTopic()).thenReturn("test-topic");
        QuestionResponse response = questionService.createQuestion(questionRequest);
        assertEquals(Arrays.asList("Option 1", "Option 2"), response.getQuestions().get(0).getOptions());
    }

    @Test
    public void testCreateQuestionWithOptionsEmptyList() {
        Question question = Question.builder()
                .questionStatement("Test Question")
                .options(Collections.emptyList())
                .build();
        List<Question> questions = Collections.singletonList(question);
        QuestionRequest questionRequest = QuestionRequest.builder()
                .requestInfo(requestInfo)
                .questions(questions)
                .build();
        when(applicationProperties.getMaxCreateLimit()).thenReturn(5);
        when(applicationProperties.getSaveQuestionTopic()).thenReturn("test-topic");
        QuestionResponse response = questionService.createQuestion(questionRequest);
        assertEquals(Collections.singletonList("NA"), response.getQuestions().get(0).getOptions());
    }

    @Test
    public void testUpdateQuestionSuccess() {
        AuditDetails auditDetails= AuditDetails.builder()
                .createdTime(System.currentTimeMillis())
                .lastModifiedTime(System.currentTimeMillis())
                .createdBy("creator")
                .lastModifiedBy("modifier")
                .build();

        Question existingQuestion = Question.builder()
                .uuid("123")
                .questionStatement("Old Question")
                .status(Status.ACTIVE)
                .auditDetails(auditDetails)
                .build();

        Question updatedQuestion = Question.builder()
                .uuid("123")
                .questionStatement("New Question")
                .status(Status.INACTIVE)
                .build();

        List<Question> existingQuesList = Collections.singletonList(existingQuestion);
        when(questionRepository.getQuestionById(any(String.class))).thenReturn(existingQuesList);
        when(applicationProperties.getUpdateQuestionTopic()).thenReturn("test-topic");

        QuestionRequest questionRequest = QuestionRequest.builder()
                .requestInfo(requestInfo)
                .questions(Collections.singletonList(updatedQuestion))
                .build();

        QuestionResponse response = questionService.updateQuestion(questionRequest);
        assertNotEquals("New Question", response.getQuestions().get(0).getQuestionStatement());
        assertEquals(Status.INACTIVE, response.getQuestions().get(0).getStatus());
        assertEquals("1", response.getQuestions().get(0).getAuditDetails().getLastModifiedBy());
    }

    @Test
    public void testUpdateQuestionNoChanges() {
        Question existingQuestion = Question.builder()
                .uuid("123")
                .questionStatement("Test Question")
                .status(Status.ACTIVE)
                .build();

        List<Question> existingQuesList = Collections.singletonList(existingQuestion);
        when(questionRepository.getQuestionById("123")).thenReturn(existingQuesList);

        QuestionRequest questionRequest = QuestionRequest.builder()
                .requestInfo(requestInfo)
                .questions(Collections.singletonList(existingQuestion))
                .build();

        assertThrows(CustomException.class, () -> questionService.updateQuestion(questionRequest));
    }

    @Test
    public void testUpdateQuestionNoChangesOnlyUUidGivenStatusNull() {
        Question existingQuestion = Question.builder()
                .uuid("123")
                .questionStatement("Test Question")
                .status(Status.ACTIVE)
                .build();

        Question updateQuestion = Question.builder()
                .uuid("123")
                .build();

        List<Question> existingQuesList = Collections.singletonList(existingQuestion);
        when(questionRepository.getQuestionById("123")).thenReturn(existingQuesList);

        QuestionRequest questionRequest = QuestionRequest.builder()
                .requestInfo(requestInfo)
                .questions(Collections.singletonList(updateQuestion))
                .build();

        assertThrows(CustomException.class, () -> questionService.updateQuestion(questionRequest));
    }

    @Test
    public void testUpdateQuestionNotFound() {
        when(questionRepository.getQuestionById("123")).thenReturn(Collections.emptyList());
        QuestionRequest questionRequest = QuestionRequest.builder()
                .requestInfo(requestInfo)
                .questions(Collections.singletonList(Question.builder().uuid("123").build()))
                .build();
        RuntimeException thrown = assertThrows(CustomException.class, () -> questionService.updateQuestion(questionRequest));
        assertEquals("question not found",thrown.getMessage());
        assertEquals(CustomException.class,thrown.getClass());
    }

    @Test
    public void testSearchQuestionByUuid() {
        Question question = Question.builder()
                .uuid("123")
                .build();
        QuestionSearchCriteria criteria = QuestionSearchCriteria.builder()
                .uuid("123")
                .pageNumber(1)
                .size(10)
                .build();

        List<Question> list=new ArrayList<>();
        list.add(question);
        when(questionRepository.fetchQuestions(criteria)).thenReturn(list);
        QuestionResponse response = questionService.searchQuestion(criteria);
        assertEquals("123", response.getQuestions().get(0).getUuid());
    }

    @Test
    public void testSearchQuestionByTenantIdAndCategoryId() {
        Question question = Question.builder()
                .tenantId("default")
                .categoryId("1")
                .build();
        QuestionSearchCriteria criteria = QuestionSearchCriteria.builder()
                .tenantId("default")
                .pageNumber(1)
                .size(10)
                .categoryId("1")
                .build();

        when(questionRepository.fetchQuestions(criteria)).thenReturn(Collections.singletonList(question));

        QuestionResponse response = questionService.searchQuestion(criteria);
        assertEquals("default", response.getQuestions().get(0).getTenantId());
        assertEquals("1", response.getQuestions().get(0).getCategoryId());
    }

    @Test
    public void testSearchQuestionInvalidCriteria() {
        QuestionSearchCriteria criteria = QuestionSearchCriteria.builder()
                .build();

        assertThrows(CustomException.class, () -> questionService.searchQuestion(criteria));
    }

    @Test
    public void testSearchQuestionInvalidCriteriaCategoryIdBlank() {
        QuestionSearchCriteria criteria = QuestionSearchCriteria.builder()
                .tenantId("default")
                .categoryId("")
                .build();

        RuntimeException thrown = assertThrows(CustomException.class, () -> questionService.searchQuestion(criteria));
        assertEquals(thrown.getMessage(),"either a (uuid) or a (tenant id and category id) is required.");
    }

    @Test
    public void testSearchQuestionInvalidPageNumber() {
        QuestionSearchCriteria criteria = QuestionSearchCriteria.builder()
                .uuid("123")
                .pageNumber(0)
                .build();

        assertThrows(IllegalArgumentException.class, () -> questionService.searchQuestion(criteria));
    }

    @Test
    public void testCreateQuestions_Success_WithinLimit() {
        when(applicationProperties.getMaxCreateLimit()).thenReturn(5);
        List<Question> questions = createQuestions(5);
        QuestionRequest request = QuestionRequest.builder().questions(questions).requestInfo(requestInfo).build();
        QuestionResponse questionResponse = questionService.createQuestion(request);

        List<Question> questionsList = questionResponse.getQuestions();
        assertEquals(questions.size(), questionsList.size());
        for (int i = 0; i < questions.size(); i++) {
            assertEquals(questions.get(i).getUuid(), questionsList.get(i).getUuid());
        }
    }

    @Test
    public void testCreateQuestions_Failure_ExceedsLimit() {
        List<Question> questions = createQuestions(6);
        when(applicationProperties.getMaxCreateLimit()).thenReturn(5);
        QuestionRequest request = QuestionRequest.builder().questions(questions).requestInfo(requestInfo).build();
        assertThrows(IllegalArgumentException.class, () -> questionService.createQuestion(request));
    }

    private List<Question> createQuestions(int count) {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(createQuestion("question" + i, "category" + i));
        }
        return questions;
    }

    private Question createQuestion(String uuid, String categoryId) {
        return Question.builder()
                .uuid(uuid)
                .tenantId("default")
                .surveyId("survey123")
                .questionStatement("Test Question")
                .status(Status.ACTIVE)
                .options(Arrays.asList("Option 1", "Option 2"))
                .type(Type.MULTIPLE_ANSWER_TYPE)
                .required(true)
                .categoryId(categoryId)
                .build();
    }
}