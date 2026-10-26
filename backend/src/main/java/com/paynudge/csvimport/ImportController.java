package com.paynudge.csvimport;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.paynudge.csvimport.ImportDtos.ImportPreview;
import com.paynudge.csvimport.ImportDtos.ImportResult;
import com.paynudge.csvimport.ImportDtos.ManualEntryRequest;
import com.paynudge.csvimport.ImportDtos.ManualEntryResult;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Preview and commit take the CSV as the multipart part "file" and, optionally for preview, the column choice as a
 * JSON part "mapping". Commit also takes the reminder policy as the form field "policyId". Size limits are in
 * {@code spring.servlet.multipart.*}. A single due typed in by hand is plain JSON.
 */
@RestController
@RequestMapping("/api/import")
class ImportController {

	private final ImportService imports;

	ImportController(ImportService imports) {
		this.imports = imports;
	}

	@PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ImportPreview preview(Caller caller, @RequestPart("file") MultipartFile file,
			@RequestPart(name = "mapping", required = false) ColumnMapping mapping) {
		return this.imports.preview(caller, bytes(file), mapping);
	}

	@PostMapping(path = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ImportResult commit(Caller caller, @RequestPart("file") MultipartFile file,
			@RequestPart(name = "mapping", required = false) ColumnMapping mapping,
			@RequestParam(name = "policyId", required = false) Long policyId) {
		return this.imports.commit(caller, bytes(file), mapping, policyId);
	}

	@PostMapping("/manual")
	@ResponseStatus(HttpStatus.CREATED)
	ManualEntryResult manual(Caller caller, @RequestBody ManualEntryRequest request) {
		return this.imports.add(caller, request);
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
