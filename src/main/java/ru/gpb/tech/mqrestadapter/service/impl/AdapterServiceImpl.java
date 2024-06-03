package ru.gpb.tech.mqrestadapter.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import ru.gpb.tech.mqrestadapter.config.FeatureToggleConfig;
import ru.gpb.tech.mqrestadapter.feign.WalletClient;
import ru.gpb.tech.mqrestadapter.mapper.WalletBalanceMapper;
import ru.gpb.tech.mqrestadapter.model.ClientRequest;
import ru.gpb.tech.mqrestadapter.model.ClientResponse;
import ru.gpb.tech.mqrestadapter.service.AdapterService;
import ru.gpb.tech.mqrestadapter.service.MqPublisher;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Класс для обработки запросов клиентов
 */
@Service
@RequiredArgsConstructor
public class AdapterServiceImpl implements AdapterService {
    private final WalletClient walletClient;
    private final WalletBalanceMapper mapper;
    private final MqPublisher publisher;
    private final FeatureToggleConfig featureToggleConfig;
    
    /**
     * Обрабатывает запрос клиента, создает ответ клиента и отправляет его через {@link MqPublisher}.
     * Если интеграция с кошельком включена, используется новая логика, иначе используется старая логика.
     *
     * @param request Объект {@link ClientRequest}, содержащий данные запроса клиента.
     *                Поля запроса включают конечную дату (dateTo), которая используется для установки
     *                последнего обновления в ответе клиента.
     */
    @Override
    public void processRequest(ClientRequest request) {
        if (featureToggleConfig.isWalletIntegrationEnabled()) {
            executeNewLogic(request);
        } else {
            executeOldLogic(request);
        }
    }
    
    private void executeOldLogic(ClientRequest clientRequest) {
        ClientResponse clientResponse = new ClientResponse();
        clientResponse.setLastUpdated(clientRequest.getDateTo());
        clientResponse.setBalance(BigDecimal.ONE);
        clientResponse.setCurrency("RUB");
        
        publisher.sendSuccess(clientResponse);
    }
    
    private void executeNewLogic(ClientRequest request) {
        Optional.ofNullable(request)
                .map(mapper::toWalletRequest)
                .map(walletClient::getBalance)
                .filter(response -> response.getStatusCode().is2xxSuccessful())
                .map(HttpEntity::getBody)
                .map(mapper::toClientResponse)
                .ifPresentOrElse(publisher::sendSuccess,
                        () -> publisher.sendError("При обработке запроса произошла ошибка"));
    }
}
