package uk.gov.hmcts.reform.sscs.service.placeholders;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.sscs.domain.FurtherEvidenceLetterType.APPELLANT_LETTER;
import static uk.gov.hmcts.reform.sscs.domain.FurtherEvidenceLetterType.REPRESENTATIVE_LETTER;
import static uk.gov.hmcts.reform.sscs.service.placeholders.PlaceholderHelper.buildCaseData;

import java.util.Map;
import junitparams.JUnitParamsRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@RunWith(JUnitParamsRunner.class)
public class FurtherEvidencePlaceholderServiceTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

    @Mock
    private PlaceholderService placeholderService;
    @Mock
    private DwpAddressLookupService dwpAddressLookup;

    @InjectMocks
    private FurtherEvidencePlaceholderService furtherEvidencePlaceholderService;

    SscsCaseData sscsCaseDataWithAppointee;

    SscsCaseData sscsCaseDataWithRep;

    SscsCaseData sscsCaseDataWithRepNoName;

    SscsCaseData sscsCaseDataWithRepNullName;

    SscsCaseData sscsCaseDataWithRepEmptyName;

    SscsCaseData sscsCaseDataWithRepNoNameNoOrg;

    SscsCaseData sscsCaseDataWithRepNoNameEmptyOrg;

    SscsCaseData sscsCaseDataWithRepNoNameNullOrg;

    @Captor
    ArgumentCaptor<Address> captor;

    @Before
    public void setup() {
        sscsCaseDataWithAppointee = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .appellant(Appellant.builder()
                    .appointee(Appointee.builder()
                        .name(Name.builder().title("Mr").firstName("Terry").lastName("Appointee").build())
                        .identity(Identity.builder().nino("JT0123456B").build())
                        .address(Address.builder()
                            .line1("HM Courts & Tribunals Service Appointee")
                            .town("Social Security & Child Support Appeals Appointee")
                            .county("Prudential Buildings Appointee")
                            .postcode("L2 5UZ")
                            .build())
                        .build())
                    .isAppointee("Yes")
                    .build())
                .build())
            .build();

        sscsCaseDataWithRep = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .rep(Representative.builder()
                    .name(Name.builder().title("Mr").firstName("Terry").lastName("Rep").build())
                    .address(Address.builder()
                        .line1("HM Courts & Tribunals Service Reps")
                        .town("Social Security & Child Support Appeals Reps")
                        .county("Prudential Buildings Reps")
                        .postcode("L2 5UZ")
                        .build())
                    .build())
                .build())
            .build();

        sscsCaseDataWithRepNoName = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .rep(Representative.builder()
                    .organisation("Nandos")
                    .address(Address.builder()
                        .line1("HM Courts & Tribunals Service Reps")
                        .town("Social Security & Child Support Appeals Reps")
                        .county("Prudential Buildings Reps")
                        .postcode("L2 5UZ")
                        .build())
                    .build())
                .build())
            .build();

        sscsCaseDataWithRepNullName = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .rep(Representative.builder()
                    .name(Name.builder().title(null).firstName(null).lastName(null).build())
                    .organisation("Nandos")
                    .address(Address.builder()
                        .line1("HM Courts & Tribunals Service Reps")
                        .town("Social Security & Child Support Appeals Reps")
                        .county("Prudential Buildings Reps")
                        .postcode("L2 5UZ")
                        .build())
                    .build())
                .build())
            .build();

        sscsCaseDataWithRepEmptyName = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .rep(Representative.builder()
                    .name(Name.builder().title("").firstName("").lastName("").build())
                    .organisation("Nandos")
                    .address(Address.builder()
                        .line1("HM Courts & Tribunals Service Reps")
                        .town("Social Security & Child Support Appeals Reps")
                        .county("Prudential Buildings Reps")
                        .postcode("L2 5UZ")
                        .build())
                    .build())
                .build())
            .build();

        sscsCaseDataWithRepNoNameNoOrg = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .rep(Representative.builder()
                    .address(Address.builder()
                        .line1("HM Courts & Tribunals Service Reps")
                        .town("Social Security & Child Support Appeals Reps")
                        .county("Prudential Buildings Reps")
                        .postcode("L2 5UZ")
                        .build())
                    .build())
                .build())
            .build();

        sscsCaseDataWithRepNoNameEmptyOrg = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .rep(Representative.builder()
                    .organisation("")
                    .address(Address.builder()
                        .line1("HM Courts & Tribunals Service Reps")
                        .town("Social Security & Child Support Appeals Reps")
                        .county("Prudential Buildings Reps")
                        .postcode("L2 5UZ")
                        .build())
                    .build())
                .build())
            .build();

        sscsCaseDataWithRepNoNameNullOrg = SscsCaseData.builder()
            .appeal(Appeal.builder()
                .benefitType(BenefitType.builder().code("PIP").build())
                .mrnDetails(MrnDetails.builder().dwpIssuingOffice("1").build())
                .rep(Representative.builder()
                    .organisation(null)
                    .address(Address.builder()
                        .line1("HM Courts & Tribunals Service Reps")
                        .town("Social Security & Child Support Appeals Reps")
                        .county("Prudential Buildings Reps")
                        .postcode("L2 5UZ")
                        .build())
                    .build())
                .build())
            .build();

    }

    @Test
    public void givenAnAppellant_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(buildCaseData(), APPELLANT_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Terry Tibbs", actual.get("name"));
        assertEquals("HM Courts & Tribunals Service", captor.getValue().getLine1());
        assertEquals("Down the road", captor.getValue().getLine2());
        assertEquals("Social Security & Child Support Appeals", captor.getValue().getTown());
        assertEquals("Prudential Buildings", captor.getValue().getCounty());
        assertEquals("L2 5UZ", captor.getValue().getPostcode());
    }

    @Test
    public void givenAnAppellantWithALongNameExceeding45Characters_thenGenerateThePlaceholdersWithTruncatedName() {
        SscsCaseData caseData = buildCaseData();
        caseData.getAppeal().getAppellant().setName(Name.builder().firstName("Jimmy").lastName("AVeryLongNameWithLotsaAdLotsAndLotsOfCharacters").build());
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(caseData, APPELLANT_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Jimmy AVeryLongNameWithLotsaAdLotsAndLotsOfCh", actual.get("name"));
    }

    @Test
    public void givenAnAppointee_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithAppointee, APPELLANT_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Terry Appointee", actual.get("name"));
        assertEquals("HM Courts & Tribunals Service Appointee", captor.getValue().getLine1());
        assertEquals("Social Security & Child Support Appeals Appointee", captor.getValue().getTown());
        assertEquals("Prudential Buildings Appointee", captor.getValue().getCounty());
        assertEquals("L2 5UZ", captor.getValue().getPostcode());
    }

    @Test
    public void givenARep_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithRep, REPRESENTATIVE_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Terry Rep", actual.get("name"));
        assertEquals("HM Courts & Tribunals Service Reps", captor.getValue().getLine1());
        assertEquals("Social Security & Child Support Appeals Reps", captor.getValue().getTown());
        assertEquals("Prudential Buildings Reps", captor.getValue().getCounty());
        assertEquals("L2 5UZ", captor.getValue().getPostcode());
    }

    @Test
    public void givenARepWithNoNameButOrg_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithRepNoName, REPRESENTATIVE_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Nandos", actual.get("name"));
    }

    @Test
    public void givenARepWithEmptyNameButOrg_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithRepEmptyName, REPRESENTATIVE_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Nandos", actual.get("name"));
    }

    @Test
    public void givenARepWithNullNameButOrg_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithRepNullName, REPRESENTATIVE_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Nandos", actual.get("name"));
    }

    @Test
    public void givenARepWithNoNameNoOrg_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithRepNoNameNoOrg, REPRESENTATIVE_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Sir/Madam", actual.get("name"));
    }

    @Test
    public void givenARepWithNoNameEmptyOrg_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithRepNoNameEmptyOrg, REPRESENTATIVE_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Sir/Madam", actual.get("name"));
    }

    @Test
    public void givenARepWithNoNameNullOrg_thenGenerateThePlaceholders() {
        Map<String, Object> actual = furtherEvidencePlaceholderService.populatePlaceholders(sscsCaseDataWithRepNoNameNullOrg, REPRESENTATIVE_LETTER);
        verify(placeholderService).build(any(), any(), captor.capture(), eq(null));

        assertEquals("Sir/Madam", actual.get("name"));
    }
}
