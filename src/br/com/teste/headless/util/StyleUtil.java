package br.com.teste.headless.util;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static org.apache.commons.lang3.StringUtils.removeEnd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.base.Function;

import lumis.portal.PortalException;
import lumis.portal.manager.ManagerFactoryInternal;
import lumis.portal.presentation.mode.PortalModes;
import lumis.portal.serviceinterface.IServiceInterfaceRenderRequest;
import lumis.portal.url.IURLManager.PathReferenceType;

public class StyleUtil
{
	/**
	 * Renderiza uma interface de lista como um JSON.
	 * @param listControl o JSON do controle de lista.
	 * @param paginationControl o JSON do controle de paginação.
	 * @param request o request da interface.
	 * @return o JSON (já convertido para String)
	 * @since %LUM_NEXT_VERSION%
	 */
	public static String renderList(JSONObject listControl, JSONObject paginationControl, IServiceInterfaceRenderRequest request) throws PortalException
	{
		// o objeto JSON de retorno 
		var resultObj = new JSONObject();
		
		// o array de linhas da lista
		var rowsArr = new JSONArray();
		resultObj.put("rows", rowsArr);
		
		// lê os campos existentes na interface
		var fields = readFields(listControl);
		
		// itera sobre as linhas do controle de lista
		for(var dataObj : getDataRows(listControl))
		{
			// lê os dados da linha
			var rowObj = renderData(dataObj, fields, request);
			
			// se a linha tem um $href (que é o link para os detalhes do conteúdo),
			// processa o link para que ele fique num formato que possa ser utilizado pelo Next
			if(dataObj.has("$href"))
			{
				var href = dataObj.getString("$href");
				href = processHref(href, request);
				rowObj.put("$href", href);
			}
			
			// adiciona os dados da linha no array de retorno
			rowsArr.put(rowObj);
		}
		
		// renderiza o controle de paginação e o adiciona (caso exista) no JSON de retorno
		var paginationObj = renderPagination(paginationControl, request);
		if(paginationObj != null)
		{
			resultObj.put("pagination", paginationObj);
		}
		
		// retorna o JSON criado, formatado como String
		return resultObj.toString(2);
	}
	
	/**
	 * Renderiza uma interface de detalhes como um JSON.
	 * @param detailsControl o JSON do controle de detalhes.
	 * @param request o request da interface.
	 * @return o JSON (já convertido para String)
	 * @since %LUM_NEXT_VERSION%
	 */
	public static String renderDetails(JSONObject detailsControl, IServiceInterfaceRenderRequest request) throws PortalException
	{
		// lê os campos existentes na interface
		var fields = readFields(detailsControl);
		
		// lê os dados dos detalhes
		var detailsRows = getDataRows(detailsControl);
		
		// se tem detalhes correspondentes, retorna o JSON formatado
		if(detailsRows.size() > 0)
		{
			return renderData(detailsRows.iterator().next(), fields, request).toString(2);
		}
		
		return new JSONObject().toString(2);
	}
	
	/**
	 * Renderiza um controle de paginação para uma interface de lista.
	 * @param paginationControl o JSON original do controle
	 * @param request o request
	 * @return um JSON do controle de paginação
	 * @since %LUM_NEXT_VERSION%
	 */
	private static JSONObject renderPagination(JSONObject paginationControl, IServiceInterfaceRenderRequest request)
	{
		// obtêm o nó "data" do controle
		var dataNode = paginationControl.optJSONObject("data");
		
		// se não tem esse nó, só retorna null
		if(dataNode == null)
		{
			return null;
		}
		
		// cria o JSON de retorno
		var resultObject = new JSONObject();
		
		// função para injetar no JSON de retorno uma propriedade 
		// existente no JSON de origem
		// além de verificar se existe a prop na origem, também a
		// converte para int
		Consumer<String> injectOptStringInt = propName -> 
		{
			if(dataNode.has(propName))
			{
				var value = Integer.parseInt(dataNode.getString(propName));
				resultObject.put(propName, value);
			}
		};

		// adiciona props da origem para o destino
		injectOptStringInt.accept("numPages");
		injectOptStringInt.accept("startRow");
		injectOptStringInt.accept("totalPages");
		injectOptStringInt.accept("endRow");
		injectOptStringInt.accept("totalRows");
		injectOptStringInt.accept("currentPage");
		
		// função que extrai uma prop chamada "$hrefQSParameter" do JSON
		// de parâmetro e retorna a string com essa URL processada
		Function<JSONObject, String> extractHref = obj -> 
		{
			var href = obj.optString("$hrefQSParameter");
			if(href == null)
			{
				return null;
			}
			
			return processHref(href, request);
		};
		
		// adiciona um link para a próxima página, caso exista
		if(dataNode.has("nextPage"))
		{
			var nextPageHref = extractHref.apply(dataNode.getJSONObject("nextPage"));
			resultObject.put("nextPage", nextPageHref);
		}
		
		// adiciona um link para a página anterior, caso exista
		if(dataNode.has("previousPage"))
		{
			var nextPageHref = extractHref.apply(dataNode.getJSONObject("previousPage"));
			resultObject.put("previousPage", nextPageHref);
		}
		
		// array de links de páginas
		var pagesArr = new JSONArray();
		var pagesOrigArr = dataNode.optJSONArray("page");
		if(pagesOrigArr != null)
		{
			resultObject.put("pages", pagesArr);
			
			for(var i = 0; i < pagesOrigArr.length(); i++)
			{
				var pageObj = pagesOrigArr.getJSONObject(i);
				var pageNum = Integer.parseInt(pageObj.getString("$"));
				var isCurrent = pageObj.has("$currentPage") && pageObj.getBoolean("$currentPage");
				var pageHref = extractHref.apply(pageObj);
				
				pagesArr.put(
					new JSONObject()
					.put("pageNum", pageNum)
					.put("isCurrent", isCurrent)
					.put("href", pageHref)
				);
			}
		}
		
		return resultObject;
	}
	
	/**
	 * Processa o link do parâmetro considerando características do request.
	 * @param href o link
	 * @param request o request
	 * @return o link processado
	 * @since %LUM_NEXT_VERSION%
	 */
	private static String processHref(String href, IServiceInterfaceRenderRequest request)
	{
		try
		{
			return ManagerFactoryInternal
				.getURLManager()
				.processHref(
					
					// o link original
					href, 
					
					// temas aplicados (considerando nenhum tema porque os artefatos linkados 
					// são conteúdos e não recursos web, como javascripts e css; então os 
					// temas não deveriam influenciar nos links)
					emptyList(), 
					
					// caminho da requisição
					request.getApplicationRequestedPath(), 
					
					// língua da requisição
					request.getLocale(), 
					
					// URL base da requisição
					request.getWebsiteBaseURL(), 
					
					// indica se a requisição é segura (HTTPS) ou não
					request.isSecure(), 
					
					// modo de navegação (forçando sempre modo usuário)
					PortalModes.MODE_NAVIGATION, 
					
					// vamos forçar o tipo de referência para ser a raiz do website
					// isso é importante para os links utilizados pelo Next ficarem de acordo
					PathReferenceType.ROOT
				);
		}
		catch (PortalException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Renderiza os dados do JSON do parâmetro de acordo com seu tipo de dados.
	 * @param data os dados
	 * @param fields os campos existentes na interface
	 * @param request a requisição da interface
	 * @return o JSON formatado de acordo
	 * @since %LUM_NEXT_VERSION%
	 */
	private static JSONObject renderData(JSONObject data, Map<String, String> fields, IServiceInterfaceRenderRequest request) throws PortalException
	{
		// o JSON de retorno
		JSONObject resultObj = new JSONObject();
		
		// itera sobre os campos existentes na interface
		for(var entry : fields.entrySet()) 
		{
			// id do campo
			var fieldId = entry.getKey();
			
			// tipo de dados do campo
			var dataType = entry.getValue();
			
			// valor existente nos dados de origem
			var optValue = data.opt(fieldId);
			
			// renderiza o valor destino baseado no tipo de dados
			var fieldValue = renderField(optValue, dataType, request);
			
			// se tem valor, adiciona no JSON resultado
			if(fieldValue != null)
			{
				resultObj.put(fieldId, fieldValue);
			}
		}
		
		return resultObj;
	}
	
	/**
	 * Renderiza o valor de um campo de acordo com seu tipo de dados.
	 * @param fieldValue o valor do campo
	 * @param dataType o tipo de dados do campo
	 * @param request a requisição à interface
	 * @return o valor convertido do campo
	 * @since %LUM_NEXT_VERSION%
	 */
	private static Object renderField(Object fieldValue, String dataType, IServiceInterfaceRenderRequest request) throws PortalException
	{
		// verifica se há algum valor
		if(fieldValue == null)
		{
			return null;
		}
		
		switch(dataType)
		{
			case "guid":
			case "string":
			case "text":
				if(fieldValue instanceof String)
				{
					return fieldValue;
				}
				else
				{
					// somente ignorando outros possíveis casos para o POC
					return null;
				}
				
			case "html":
				if(fieldValue instanceof String)
				{
					// processa o HTML do campo para arrumar as URLs
					var processedHTML = 
						ManagerFactoryInternal
						.getURLManager()
						.processHTML(
							
							// o HTML original cadastrado pelo publicador
							(String) fieldValue,
							
							// a língua do request
							request.getLocale()
						);
					
					// como o processamento anterior iria gerar URLs absolutas (já que o método utilizado não passa 
					// nenhum contexto pro LumisXP), vamos remover o host e, assim, transformar elas em URLs 
					// relativas à raiz do website (sem incluir o host)
					
					// primeiro, removemos o "/" (caso exista) do final do website acessado no request)
					var accessedWebsite = removeEnd(request.getWebsiteBaseURL().toString(), "/");
					
					// depois, removemos a referência a esse host do HTML gerado
					processedHTML = processedHTML.replaceAll(accessedWebsite, "");
					return processedHTML;
				}
				else
				{
					// somente ignorando outros possíveis casos para o POC
					return null;
				}
				
			case "media":
				if(fieldValue instanceof JSONObject)
				{
					// cast o valor para um JSON object
					JSONObject fieldValueObj = (JSONObject) fieldValue;
					
					// uma lista de propriedades de URLs que deverão ser processadas
					var propsToBeProcessed = Arrays.asList("downloadInlineHref", "downloadHref", "href", "icon");
					
					// itera nessas propriedades
					for(var propToBeProcessed : propsToBeProcessed)
					{
						if(fieldValueObj.has(propToBeProcessed))
						{
							var value = fieldValueObj.getString(propToBeProcessed);
							if(value != null)
							{
								// se há valor para essa propriedade, processe o link dela
								// removendo algumas marcações que o LumisXP gera
								value = value.replace("#lumFixPathReference-", "");
								value = value.replace("-lumFixPathReference#", "");
								value = processHref(value, request);
								fieldValueObj.put(propToBeProcessed, value);
							}
						}
					}
					
					return fieldValueObj;
				}
				else
				{
					// somente ignorando outros possíveis casos para o POC
					return null;
				}
				
			default:
				return null; // outros casos ignorados para o POC
				
		}
	}
	
	/**
	 * Retorna os nós de dados do controle
	 * @param control o controle
	 * @return os nós de dados
	 * @since %LUM_NEXT_VERSION%
	 */
	private static List<JSONObject> getDataRows(JSONObject control)
	{
		var dataNode = control.optJSONObject("data");
		if(dataNode == null)
		{
			return emptyList();
		}
		
		var rows = dataNode.optJSONArray("row");
		if(rows == null)
		{
			return emptyList();
		}
		
		var list = new ArrayList<JSONObject>();
		rows.forEach(o -> list.add((JSONObject) o));
		return unmodifiableList(list);
	}
	
	/**
	 * Lê os campos existentes na interface.
	 * @param control o controle (lista ou detalhes)
	 * @return os campos e seus data types
	 * @since %LUM_NEXT_VERSION%
	 */
	private static Map<String, String> readFields(JSONObject control)
	{
		var fieldsObj = control.optJSONObject("fields");
		
		if(fieldsObj == null)
		{
			return emptyMap();
		}
		
		var fieldsArr = fieldsObj.optJSONArray("field");

		if(fieldsArr == null)
		{
			return emptyMap();
		}
		
		Map<String, String> returnMap = new LinkedHashMap<>();
		
		for(var i = 0; i < fieldsArr.length(); i++) 
		{
			var fieldObj = fieldsArr.getJSONObject(i);
			var fieldId = fieldObj.getString("$id");
			var dataType = fieldObj.getString("$dataType");
			
			returnMap.put(fieldId, dataType);
		}
		
		return Collections.unmodifiableMap(returnMap);
	}
}
