package com.aechak.admin.support;

import com.aechak.application.file.port.FileKey;
import com.aechak.application.file.port.FileStorage;
import com.aechak.application.file.port.IssueFileUrl;
import com.aechak.application.file.port.enums.FileType;
import com.aechak.application.file.port.enums.UploadPurpose;
import com.aechak.domain.support.Ulid;

/** 실 S3 대신 키 규칙만 흉내냄 — 어드민 테스트는 다운로드 URL 발급이 주 관심사. */
public class FakeFileStorage implements FileStorage {

    @Override
    public IssueFileUrl issueUploadUrl(UploadPurpose purpose, FileType fileType, long userId) {
        String key = FileKey.INSTANCE.tmpPrefixOf(userId, purpose) + Ulid.INSTANCE.generate() + "." + fileType.getExtension();
        return new IssueFileUrl("https://fake-presigned.local/" + key, key);
    }

    @Override
    public String promote(String tmpKey, UploadPurpose purpose) {
        return purpose.getPrefix() + "/" + tmpKey.substring(tmpKey.lastIndexOf('/') + 1);
    }

    @Override
    public String publicUrlOf(String key) {
        return "https://fake-cdn.local/" + key;
    }

    @Override
    public String issueDownloadUrl(String key, UploadPurpose purpose) {
        return "https://fake-download.local/" + key;
    }
}
