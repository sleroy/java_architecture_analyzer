package br.com.semeru.rest.implementations;

import java.io.File;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import br.com.semeru.service.ReportService;

@Path("/report")
@RequestScoped
@Api(value = "/report", description = "Building a report in PDF!")
public class ReportRESTService{
    
	@Inject
    ReportService report;
	
	public ReportRESTService() {}

	@GET
    @Produces("application/pdf")
	@ApiOperation(value = "Building a report in PDF!", notes = "Building a report in PDF!")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 500, message = "Houston we have a problem")})
    public Response listAllMembers() throws Exception {
    	File file = report.generatePDF();
    	ResponseBuilder response = Response.ok((Object) file);
    	response.header("Content-Disposition", "attachment; filename=output.pdf");
    	return response.build();
    }
}
