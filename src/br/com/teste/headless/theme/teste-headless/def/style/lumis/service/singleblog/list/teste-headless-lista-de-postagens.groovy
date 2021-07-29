<%
	// lê o controle de lista (em formato Map)
	def control = lum_xpath.getMap("//control[@id='list.tabulardata']")
	
	// converte o controle de lista para um objeto JSON
	def controlJSON = new org.json.JSONObject(control)
	
	// lê o controle de paginação (em formato Map)
	def paginationControl = lum_xpath.getMap("//control[@id='list.pagination']")
	
	// converte o controle de pagição para um objeto JSON
	def paginationControlJSON = new org.json.JSONObject(paginationControl)
	
	// renderiza o conteúdo da interface como uma string JSON
	def resultStr = br.com.teste.headless.util.StyleUtil.renderList(
	
		// o JSON do controle de lista
		controlJSON, 
		
		// o JSON do controle de paginação
		paginationControlJSON,
		
		// a requisição sendo tratada 
		lum_serviceInterfaceRequest
	)
	
	// imprime o resultado
	print(resultStr)
%>