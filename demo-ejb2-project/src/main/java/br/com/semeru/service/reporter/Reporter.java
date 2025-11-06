package br.com.semeru.service.reporter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import br.com.semeru.fakedata.DataBean;
import br.com.semeru.fakedata.DataBeanMaker;

@RequestScoped
public class Reporter {
        
    @Inject private Logger log;
    
    @Produces
    @Named
    public File makeReport() throws Exception {
	
    		InputStream inputStream = getClass().getResourceAsStream("template/test_jasper.jrxml");
            
            long start = System.currentTimeMillis();
            log.info("Init the export process");
            
            DataBeanMaker dataBeanMaker = new DataBeanMaker();
            ArrayList<DataBean> dataBeanList = dataBeanMaker.getDataBeanList();
            JRBeanCollectionDataSource beanColDataSource = new JRBeanCollectionDataSource(dataBeanList);
           
            @SuppressWarnings("rawtypes")
			Map parameters = new HashMap();
            JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport,parameters, beanColDataSource);
            File pdf = File.createTempFile("output.", ".pdf");
            JasperExportManager.exportReportToPdfStream(jasperPrint, new FileOutputStream(pdf));
            log.info("Export process finished with " + calculateTime(start));
            return pdf;
    }

    private long calculateTime(Long start) {
            return ((System.currentTimeMillis() - start) / 1000);
    }
}