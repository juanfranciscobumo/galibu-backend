package com.galibu.backend.geo;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final DepartmentRepository departments;
    private final MunicipalityRepository municipalities;
    private final CategoryRepository categories;

    public GeoController(
        DepartmentRepository departments,
        MunicipalityRepository municipalities,
        CategoryRepository categories
    ) {
        this.departments = departments;
        this.municipalities = municipalities;
        this.categories = categories;
    }

    public record NamedDto(Long id, String name) {
    }

    public record DepartmentTreeDto(String name, List<String> municipalities) {
    }

    public record CategoryRequest(String name) {
    }

    @GetMapping("/departments")
    public List<NamedDto> departments() {
        return departments.findAllByOrderByNameAsc().stream()
            .map(d -> new NamedDto(d.getId(), d.getName()))
            .toList();
    }

    @GetMapping("/departments/{departmentId}/municipalities")
    public List<NamedDto> municipalities(@PathVariable Long departmentId) {
        if (!departments.existsById(departmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Departamento no existe");
        }
        return municipalities.findByDepartmentIdOrderByNameAsc(departmentId).stream()
            .map(m -> new NamedDto(m.getId(), m.getName()))
            .toList();
    }

    @GetMapping("/tree")
    public List<DepartmentTreeDto> tree() {
        List<Department> all = departments.findAllByOrderByNameAsc();
        return all.stream()
            .map(d -> new DepartmentTreeDto(
                d.getName(),
                municipalities.findByDepartmentIdOrderByNameAsc(d.getId()).stream()
                    .map(Municipality::getName)
                    .toList()))
            .toList();
    }

    @GetMapping("/categories")
    public List<NamedDto> categories() {
        return categories.findAllByOrderByNameAsc().stream()
            .map(c -> new NamedDto(c.getId(), c.getName()))
            .toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public NamedDto addCategory(@Valid @RequestBody CategoryRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre requerido");
        }
        if (categories.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría ya existe");
        }
        Category saved = categories.save(new Category(null, name));
        return new NamedDto(saved.getId(), saved.getName());
    }
}
