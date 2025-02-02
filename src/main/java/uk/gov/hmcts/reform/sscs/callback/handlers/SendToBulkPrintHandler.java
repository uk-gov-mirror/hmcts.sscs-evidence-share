package uk.gov.hmcts.reform.sscs.callback.handlers;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import feign.FeignException;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.sscs.callback.CallbackHandler;
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.CallbackType;
import uk.gov.hmcts.reform.sscs.ccd.callback.DispatchPriority;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.config.EvidenceShareConfig;
import uk.gov.hmcts.reform.sscs.docmosis.domain.DocumentHolder;
import uk.gov.hmcts.reform.sscs.docmosis.domain.Pdf;
import uk.gov.hmcts.reform.sscs.exception.*;
import uk.gov.hmcts.reform.sscs.factory.DocumentRequestFactory;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.model.BulkPrintInfo;
import uk.gov.hmcts.reform.sscs.service.DocumentManagementServiceWrapper;
import uk.gov.hmcts.reform.sscs.service.EvidenceManagementService;
import uk.gov.hmcts.reform.sscs.service.PrintService;

/*
    This code is deprecated as it is for paper cases and everything is now digital.
    It has been left in as it may need to be brought back in for future appeal types
     */

@Slf4j
@Service
public class SendToBulkPrintHandler implements CallbackHandler<SscsCaseData> {

    private static final String DM_STORE_USER_ID = "sscs";
    private static final String SENT_TO_DWP = "Sent to DWP";
    private final DispatchPriority dispatchPriority;

    private final DocumentManagementServiceWrapper documentManagementServiceWrapper;

    private final DocumentRequestFactory documentRequestFactory;

    private final EvidenceManagementService evidenceManagementService;

    private final PrintService bulkPrintService;

    private final EvidenceShareConfig evidenceShareConfig;

    private final CcdService ccdService;

    private final IdamService idamService;

    @Autowired
    public SendToBulkPrintHandler(DocumentManagementServiceWrapper documentManagementServiceWrapper,
                                  DocumentRequestFactory documentRequestFactory,
                                  EvidenceManagementService evidenceManagementService,
                                  PrintService bulkPrintService,
                                  EvidenceShareConfig evidenceShareConfig,
                                  CcdService ccdService,
                                  IdamService idamService
    ) {
        this.dispatchPriority = DispatchPriority.LATE;
        this.documentManagementServiceWrapper = documentManagementServiceWrapper;
        this.documentRequestFactory = documentRequestFactory;
        this.evidenceManagementService = evidenceManagementService;
        this.bulkPrintService = bulkPrintService;
        this.evidenceShareConfig = evidenceShareConfig;
        this.ccdService = ccdService;
        this.idamService = idamService;
    }

    @Override
    public boolean canHandle(CallbackType callbackType, Callback<SscsCaseData> callback) {
        requireNonNull(callback, "callback must not be null");
        requireNonNull(callbackType, "callbacktype must not be null");

        return callbackType.equals(CallbackType.SUBMITTED)
            && ((callback.getEvent() == EventType.VALID_APPEAL_CREATED
                || callback.getEvent() == EventType.DRAFT_TO_VALID_APPEAL_CREATED
                || callback.getEvent() == EventType.VALID_APPEAL
                || callback.getEvent() == EventType.INTERLOC_VALID_APPEAL
                || callback.getEvent() == EventType.APPEAL_TO_PROCEED
                || callback.getEvent() == EventType.SEND_TO_DWP)
            && !callback.getCaseDetails().getCaseData().isTranslationWorkOutstanding())
            || callback.getEvent() == EventType.RESEND_TO_DWP;

    }

    @Override
    public void handle(CallbackType callbackType, Callback<SscsCaseData> callback) {
        if (!canHandle(callbackType, callback)) {
            throw new IllegalStateException("Cannot handle callback");
        }

        SscsCaseData caseData = callback.getCaseDetails().getCaseData();
        BulkPrintInfo bulkPrintInfo = null;

        try {
            bulkPrintInfo = bulkPrintCase(callback);
        } catch (NonPdfBulkPrintException | UnableToContactThirdPartyException | NoDl6DocumentException e) {
            log.info(format("Error when bulk-printing caseId: %s. %s", callback.getCaseDetails().getId(), e.getMessage()), e);
            updateCaseToFlagError(caseData, e.getMessage());
        } catch (Exception e) {
            log.info("Error when bulk-printing caseId: {}", callback.getCaseDetails().getId(), e);
            updateCaseToFlagError(caseData, "Send to DWP Error event has been triggered from Evidence Share service");
        }
        updateCaseToSentToDwp(callback, caseData, bulkPrintInfo);
    }

    @Override
    public DispatchPriority getPriority() {
        return this.dispatchPriority;
    }

    private void updateCaseToFlagError(SscsCaseData caseData, String description) {
        caseData.setHmctsDwpState("failedSending");
        ccdService.updateCase(caseData,
            Long.valueOf(caseData.getCcdCaseId()),
            EventType.SENT_TO_DWP_ERROR.getCcdType(),
            "Send to DWP Error",
            description,
            idamService.getIdamTokens());
    }

    private void updateCaseToSentToDwp(Callback<SscsCaseData> sscsCaseDataCallback, SscsCaseData caseData,
                                       BulkPrintInfo bulkPrintInfo) {
        if (bulkPrintInfo != null) {
            BulkPrintInfo info = BulkPrintInfo.builder()
                .uuid(null)
                .allowedTypeForBulkPrint(false)
                .desc("Case state is now sent to DWP")
                .build();
            if (State.READY_TO_LIST.getId().equals(caseData.getCreatedInGapsFrom())) {
                caseData.setDwpState(DwpState.UNREGISTERED.getId());
            }
            caseData.setHmctsDwpState("sentToDwp");
            caseData.setDateSentToDwp(LocalDate.now().toString());
            ccdService.updateCase(caseData, Long.valueOf(caseData.getCcdCaseId()),
                EventType.SENT_TO_DWP.getCcdType(), SENT_TO_DWP, bulkPrintInfo.getDesc(),
                idamService.getIdamTokens());
            if (bulkPrintInfo.isAllowedTypeForBulkPrint()) {
                log.info("Case sent to dwp for case id {} with returned value {}",
                    sscsCaseDataCallback.getCaseDetails().getId(), bulkPrintInfo.getUuid());
            }
        }
    }

    private BulkPrintInfo bulkPrintCase(Callback<SscsCaseData> sscsCaseDataCallback) {
        SscsCaseData caseData = sscsCaseDataCallback.getCaseDetails().getCaseData();
        if (isAllowedReceivedTypeForBulkPrint(sscsCaseDataCallback.getCaseDetails().getCaseData())) {

            log.info("Processing bulk print tasks for case id {}", sscsCaseDataCallback.getCaseDetails().getId());

            DocumentHolder holder = documentRequestFactory.create(sscsCaseDataCallback.getCaseDetails().getCaseData(),
                sscsCaseDataCallback.getCaseDetails().getCaseData().getCaseCreated());

            if (holder.getTemplate() == null) {
                throw new BulkPrintException(
                    format("Failed to send to bulk print for case %s because no template was found",
                        caseData.getCcdCaseId()));
            }
            log.info("Generating DL document for case id {}", sscsCaseDataCallback.getCaseDetails().getId());

            final IdamTokens idamTokens;
            try {
                idamTokens = idamService.getIdamTokens();
            } catch (FeignException e) {
                throw new UnableToContactThirdPartyException("idam", e);
            }
            documentManagementServiceWrapper.generateDocumentAndAddToCcd(holder, caseData, idamTokens);
            List<SscsDocument> sscsDocuments = getSscsDocumentsToPrint(caseData.getSscsDocument());
            if (CollectionUtils.isEmpty(sscsDocuments)
                    || !documentManagementServiceWrapper.checkIfDlDocumentAlreadyExists(sscsDocuments)) {
                throw new NoDl6DocumentException();
            }
            List<Pdf> existingCasePdfs = toPdf(sscsDocuments);

            log.info("Sending to bulk print for case id {}", sscsCaseDataCallback.getCaseDetails().getId());
            caseData.setDateSentToDwp(LocalDate.now().toString());

            Optional<UUID> id = bulkPrintService.sendToBulkPrint(existingCasePdfs, caseData);

            if (id.isPresent()) {
                BulkPrintInfo info = BulkPrintInfo.builder()
                    .uuid(id.get())
                    .allowedTypeForBulkPrint(true)
                    .desc(buildEventDescription(existingCasePdfs, id.get()))
                    .build();

                return info;
            } else {
                throw new BulkPrintException(
                    format("Failed to send to bulk print for case %s. No print id returned",
                        caseData.getCcdCaseId()));
            }

        } else {
            log.info("Case not valid to send to bulk print for case id {}", sscsCaseDataCallback.getCaseDetails().getId());

            return BulkPrintInfo.builder()
                .uuid(null)
                .allowedTypeForBulkPrint(false)
                .desc("Case state is now sent to DWP")
                .build();
        }
    }

    private String buildEventDescription(List<Pdf> pdfs, UUID bulkPrintId) {
        List<String> arr = new ArrayList<>();

        for (Pdf pdf : pdfs) {
            arr.add(pdf.getName());
        }

        return "Case has been sent to the DWP via Bulk Print with bulk print id: "
            + bulkPrintId
            + " and with documents: "
            + String.join(", ", arr);
    }

    private boolean isAllowedReceivedTypeForBulkPrint(SscsCaseData caseData) {
        return nonNull(caseData)
            && nonNull(caseData.getAppeal())
            && nonNull(caseData.getAppeal().getReceivedVia())
            && nonNull(caseData.getCreatedInGapsFrom())
            && !caseData.getCreatedInGapsFrom().equals(State.READY_TO_LIST.getId())
            && evidenceShareConfig.getSubmitTypes().stream().anyMatch(caseData.getAppeal().getReceivedVia()::equalsIgnoreCase);
    }

    private List<SscsDocument> getSscsDocumentsToPrint(List<SscsDocument> sscsDocument) {
        if (sscsDocument == null) {
            return Collections.emptyList();
        }

        Supplier<Stream<SscsDocument>> sscsDocumentStream = () -> sscsDocument.stream()
            .filter(doc -> nonNull(doc)
                && nonNull(doc.getValue())
                && nonNull(doc.getValue().getDocumentFileName())
                && nonNull(doc.getValue().getDocumentType())
                && nonNull(doc.getValue().getDocumentLink())
                && nonNull(doc.getValue().getDocumentLink().getDocumentUrl())
                && StringUtils.containsIgnoreCase(doc.getValue().getDocumentFileName(), ".pdf")
            );

        return buildStreamOfDocuments(sscsDocumentStream);
    }

    private List<Pdf> toPdf(List<SscsDocument> sscsDocuments) {

        List<Pdf> pdfs = new ArrayList<>();
        for (SscsDocument doc : sscsDocuments) {
            pdfs.add(new Pdf(toBytes(doc), doc.getValue().getDocumentFileName()));
        }

        return pdfs;
    }

    private List<SscsDocument> buildStreamOfDocuments(Supplier<Stream<SscsDocument>> sscsDocumentStream) {
        Stream<SscsDocument> dlDocs = sscsDocumentStream.get().filter(doc -> doc.getValue().getDocumentType().equals("dl6") || doc.getValue().getDocumentType().equals("dl16"));

        Stream<SscsDocument> appealDocs = sscsDocumentStream.get().filter(doc -> doc.getValue().getDocumentType().equals("sscs1"));

        Stream<SscsDocument> allOtherDocs = sscsDocumentStream.get().filter(doc -> !doc.getValue().getDocumentType().equals("dl6")
            && !doc.getValue().getDocumentType().equals("dl16")
            && !doc.getValue().getDocumentType().equals("sscs1"));

        return Stream.concat(Stream.concat(dlDocs, appealDocs), allOtherDocs).collect(Collectors.toList());
    }

    private byte[] toBytes(SscsDocument sscsDocument) {
        try {
            return evidenceManagementService.download(
                URI.create(sscsDocument.getValue().getDocumentLink().getDocumentUrl()),
                DM_STORE_USER_ID
            );
        } catch (FeignException e) {
            throw new UnableToContactThirdPartyException("dm-store", e);
        }
    }
}
