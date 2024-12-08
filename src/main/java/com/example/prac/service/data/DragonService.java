package com.example.prac.service.data;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.StreamSupport;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.procedure.ProcedureCall;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.prac.dto.data.DragonDTO;
import com.example.prac.dto.data.OwnerDTO;
import com.example.prac.exceptions.DragonWithSameNameException;
import com.example.prac.exceptions.NotEnoughRightsException;
import com.example.prac.exceptions.ResourceNotFoundException;
import com.example.prac.mappers.Mapper;
import com.example.prac.model.auth.Role;
import com.example.prac.model.data.Coordinates;
import com.example.prac.model.data.Dragon;
import com.example.prac.model.data.DragonCave;
import com.example.prac.model.data.DragonHead;
import com.example.prac.model.data.Person;
import com.example.prac.repository.data.CoordinatesRepository;
import com.example.prac.repository.data.DragonCaveRepository;
import com.example.prac.repository.data.DragonHeadRepository;
import com.example.prac.repository.data.DragonRepository;
import com.example.prac.repository.data.PersonRepository;
import com.example.prac.service.auth.AuthenticationService;

import jakarta.persistence.ParameterMode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DragonService {
    private final DragonRepository dragonRepository;
    private final Mapper<Dragon, DragonDTO> dragonMapper;
    private final SessionFactory sessionFactory;
    private final AuthenticationService authenticationService;
    private final CoordinatesRepository coordinatesRepository;
    private final DragonCaveRepository dragonCaveRepository;
    private final DragonHeadRepository dragonHeadRepository;
    private final PersonRepository personRepository;
    private final ModelMapper modelMapper;
    private final Random r = new Random();

    public Integer getTotalAge() {
        try (Session session = sessionFactory.openSession()) {
            return session.createNativeQuery("SELECT * FROM total_age()", Integer.class).getSingleResult();
        }
    }

    public Optional<DragonDTO> getDragonWithGigachadKiller() {
        try (Session session = sessionFactory.openSession()) {
            Long dragonID = session.createNativeQuery("SELECT * FROM get_dragon_id_with_gigachad_killer()", Long.class)
                    .getSingleResult();

            return findById(dragonID);
        }
    }

    public Optional<DragonDTO> getDragonWithTheDeepestCave() {
        try (Session session = sessionFactory.openSession()) {
            Long dragonID = session.createNativeQuery("SELECT * FROM get_dragon_with_the_deepest_cave()", Long.class)
                    .getSingleResult();

            return findById(dragonID);
        }
    }

    public List<DragonDTO> findDragonsByNameSubstring(String nameSubstring) {
        try (Session session = sessionFactory.openSession()) {
            List<Long> dragonIDs = session
                    .createNativeQuery("SELECT * FROM get_dragon_ids_by_name_substring(:nameSubstring)", Long.class)
                    .setParameter("nameSubstring", nameSubstring)
                    .list();

            return dragonIDs.stream().map(id -> findById(id).get()).toList();
        }
    }

    public void createKillersGang() {
        try (ProcedureCall call = sessionFactory.openSession().createStoredProcedureCall("create_killers_gang")) {
            var gangID = generateRandomDigitalID();
            call
                    .registerStoredProcedureParameter(1, String.class, ParameterMode.IN)
                    .registerStoredProcedureParameter(2, String.class, ParameterMode.IN)
                    .registerStoredProcedureParameter(3, String.class, ParameterMode.IN)
                    .setParameter(1, generatePassportId(gangID))
                    .setParameter(2, generatePassportId(gangID))
                    .setParameter(3, generatePassportId(gangID))
                    .execute();
        }
    }

    public DragonDTO save(DragonDTO dragonDTO) throws DragonWithSameNameException {
        System.out.printf("\n\n\n\n\nimporter: name %s passportID %s\n\n\n\n\n\n", dragonDTO.getName(),
                dragonDTO.getKiller().getPassportID());

        List<Dragon> existingDragonsWithSameName = dragonRepository.findByName(dragonDTO.getName());

        if (!existingDragonsWithSameName.isEmpty()) {
            throw new DragonWithSameNameException(dragonDTO.getName());
        }

        Dragon dragon = dragonMapper.mapFrom(dragonDTO);
        dragon.setCreationDate(new Date());
        dragon.setDragonOwner(authenticationService.getCurrentUser());

        return dragonMapper.mapTo(dragonRepository.save(dragon));
    }

    public List<DragonDTO> findAllDragons() {
        return StreamSupport.stream(dragonRepository.findAll().spliterator(), false)
                .map(dragonMapper::mapTo).toList();
    }

    public Optional<DragonDTO> findById(Long dragonId) {
        Optional<Dragon> optionalDragon = dragonRepository.findById(dragonId);
        return optionalDragon.map(dragonMapper::mapTo);
    }

    public boolean isExists(Long dragonId) {
        return dragonRepository.existsById(dragonId);
    }

    public DragonDTO partialUpdate(Long dragonId, DragonDTO dragonDTO) {
        dragonDTO.setId(dragonId);
        return dragonRepository.findById(dragonId).map(existingDragon -> {
            if (!checkUserOwnsDragon(existingDragon)) {
                throw new NotEnoughRightsException("User hasn't enough right to update this object");
            }

            Dragon dragonUpdate = dragonMapper.mapFrom(dragonDTO);

            Optional.ofNullable(dragonUpdate.getName()).ifPresent(existingDragon::setName);
            Optional.ofNullable(dragonUpdate.getCoordinates()).ifPresent(existingDragon::setCoordinates);
            Optional.ofNullable(dragonUpdate.getCave()).ifPresent(existingDragon::setCave);
            Optional.ofNullable(dragonUpdate.getKiller()).ifPresent(existingDragon::setKiller);
            Optional.ofNullable(dragonUpdate.getAge()).ifPresent(existingDragon::setAge);
            Optional.ofNullable(dragonUpdate.getColor()).ifPresent(existingDragon::setColor);
            Optional.ofNullable(dragonUpdate.getType()).ifPresent(existingDragon::setType);
            Optional.ofNullable(dragonUpdate.getCharacter()).ifPresent(existingDragon::setCharacter);
            Optional.ofNullable(dragonUpdate.getHead()).ifPresent(existingDragon::setHead);

            return dragonMapper.mapTo(dragonRepository.save(existingDragon));
        }).orElseThrow(() -> new ResourceNotFoundException(Dragon.class));
    }

    public void delete(Long dragonId) {
        dragonRepository.findById(dragonId).ifPresentOrElse(dragon -> {
            if ((authenticationService.getCurrentUser().getRole() == Role.ADMIN && dragon.getCanBeEditedByAdmin()) ||
                    checkUserOwnsDragon(dragon)) {
                dragonRepository.deleteById(dragonId);
            } else {
                throw new NotEnoughRightsException("User hasn't enough right to delete this object");
            }
        }, () -> new ResourceNotFoundException(Dragon.class));
    }

    @Transactional
    public void saveImportedDragonsList(List<DragonDTO> dragons) {
        dragons.forEach(d -> {
            var coordinates = modelMapper.map(d.getCoordinates(), Coordinates.class);
            coordinatesRepository.save(coordinates);
            d.getCoordinates().setId(coordinates.getId());

            var cave = modelMapper.map(d.getCave(), DragonCave.class);
            dragonCaveRepository.save(cave);
            d.getCave().setId(cave.getId());

            var head = modelMapper.map(d.getHead(), DragonHead.class);
            dragonHeadRepository.save(head);
            d.getHead().setId(head.getId());

            if (d.getKiller() != null) {
                var person = modelMapper.map(d.getKiller(), Person.class);
                personRepository.save(person);
                d.getKiller().setId(person.getId());
            }

            var userDTO = modelMapper.map(authenticationService.getCurrentUser(), OwnerDTO.class);
            d.setOwner(userDTO);

            save(d);
        });
    }

    private boolean checkUserOwnsDragon(Dragon dragon) {
        return authenticationService.getCurrentUser().getUsername().equals(dragon.getDragonOwner().getUsername());
    }

    private String generatePassportId(int gangId) {
        return String.format("GANG-%d-%d", gangId, generateRandomDigitalID());
    }

    private int generateRandomDigitalID() {
        return r.nextInt(1000, 9999);
    }
}