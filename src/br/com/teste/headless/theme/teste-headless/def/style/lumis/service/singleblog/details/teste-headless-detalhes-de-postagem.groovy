<%
	// lê o controle de detalhes (em formato Map)
	def control = lum_xpath.getMap("//control[@id='autoLayout.details']")
	
	// converte o controle de detalhes para um objeto JSON
	def controlJSON = new org.json.JSONObject(control)
	
	// renderiza o conteúdo da interface como uma string JSON
	def resultStr = br.com.teste.headless.util.StyleUtil.renderDetails(
		
		// o JSON do controle de detalhes
		controlJSON, 
		
		// a requisição sendo tratada 
		lum_serviceInterfaceRequest
	)
	
	// imprime o resultado
	print(resultStr)
%>