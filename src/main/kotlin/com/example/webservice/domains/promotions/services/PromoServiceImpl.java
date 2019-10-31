package com.example.webservice.domains.promotions.services;


import com.example.webservice.commons.PageAttr;
import com.example.webservice.commons.utils.Validator;
import com.example.webservice.domains.fileuploads.services.FileUploadService;
import com.example.webservice.domains.firebase.models.dto.NotificationData;
import com.example.webservice.domains.firebase.models.dto.PushNotification;
import com.example.webservice.domains.firebase.services.NotificationService;
import com.example.webservice.domains.promotions.models.entities.Promo;
import com.example.webservice.domains.promotions.repositories.PromoRepository;
import com.example.webservice.exceptions.forbidden.ForbiddenException;
import com.example.webservice.exceptions.invalid.InvalidException;
import com.example.webservice.exceptions.notfound.NotFoundException;
import com.example.webservice.exceptions.unknown.UnknownException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromoServiceImpl implements PromoService {

    private final PromoRepository promoRepo;
    private final NotificationService notificationService;
    private final FileUploadService fileUploadService;

    @Autowired
    public PromoServiceImpl(PromoRepository promoRepo, NotificationService notificationService, FileUploadService fileUploadService) {
        this.promoRepo = promoRepo;
        this.notificationService = notificationService;
        this.fileUploadService = fileUploadService;
    }

    @Override
    public Page<Promo> findAll(int page) {
        return this.promoRepo.findAll(PageAttr.getPageRequest(page));
    }

    @Override
    public Promo save(Promo promo) throws InvalidException {
        if (promo == null) throw new IllegalArgumentException("Promo can not be empty!");
        if (Validator.nullOrEmpty(promo.getTitle()))
            throw new InvalidException("Title can not be null or empty!");
        return this.promoRepo.save(promo);
    }

    @Override
    public Promo findOne(Long id) throws NotFoundException {
        Promo promo = this.promoRepo.findById(id).orElse(null);
        if (promo == null) throw new NotFoundException("Could not find promo with id " + id);
        return promo;
    }

    @Override
    public List<Promo> getLatestPromotions() {
        return this.promoRepo.findByActiveOrderByIdDesc(true);
    }

    @Override
    public void notifyUser(Long promoId) throws NotFoundException, ForbiddenException, UnknownException, InvalidException, JsonProcessingException {
        Promo promo = this.promoRepo.findById(promoId).orElse(null);
        if (promo == null) throw new NotFoundException("Cound not find promo with id: " + promoId);
        if (!promo.isActive()) throw new ForbiddenException("Can not notify users. Promotion is not active!");
        NotificationData data = new NotificationData();
        data.setTitle(promo.getTitle());
        String description = promo.getDescription();
        if (description == null) description = "";
        String brief = description.substring(0, Math.min(description.length(), 100));
        data.setMessage(brief);
        if (Promo.Priority.NORMAL.getValue().equals(promo.getPriority()))
            data.setType(PushNotification.Type.PROMOTION.getValue());
        else if (Promo.Priority.HIGH.getValue().equals(promo.getPriority()))
            data.setType(PushNotification.Type.ALERT.getValue());

        PushNotification notification = new PushNotification(null, data);
        notification.setTo("/topics/users");
        this.notificationService.sendNotification(notification);
    }


}
