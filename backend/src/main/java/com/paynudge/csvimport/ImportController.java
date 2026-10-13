package com.paynudge.csvimport;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.paynudge.csvimport.ImportDtos.ImportPreview;
import com.paynudge.csvimport.ImportDtos.ImportResult;
import com.paynudge.security.Caller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Both endpoints take the CSV as the multipart part "file" and, optionally for preview, the column choice as a JSON
 * part "mapping". Commit also takes the reminder policy as the form field "policyId". Size limits are in
 * {@code spring.servlet.multipart.*}.
 */
@RestController
@RequestMapping(path = "/api/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
class ImportController {

	private final ImportService imports;

	ImportController(ImportService imports) {
		this.imports = imports;
	}

	@PostMapping("/preview")
	ImportPreview preview(Caller caller, @RequestPart("file") MultipartFile file,
			@RequestPart(name = "mapping", required = false) ColumnMapping mapping) {
		return this.imports.preview(caller, bytes(file), mapping);
	}

	@PostMapping("/commit")
	ImportResult commit(Caller caller, @RequestPart("file") MultipartFile file,
			@RequestPart(name = "mapping", required = false) ColumnMapping mapping,
			@RequestParam(name = "policyId", required = false) Long policyId) {
		return this.imports.commit(caller, bytes(file), mapping, policyId);
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
