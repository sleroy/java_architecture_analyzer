package com.example.ejbapp.service;

import java.io.File;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.example.ejbapp.service.reporter.Reporter;

@Stateless
public class ReportService {

	@Inject private Reporter reporter;
	
	public File generatePDF() throws Exception {
		return reporter.makeReport();
	}
}