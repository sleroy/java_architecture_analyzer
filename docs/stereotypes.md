from textwrap import dedent
path = "/mnt/data/stereotypes.md"

content = dedent("""
# Class Stereotypes — EJB2 → Spring Boot Migration

This catalog defines the **architectural stereotypes** detected in an EJB 2 / J2EE codebase.
Each stereotype drives classification and triggers specific migration rules toward **Spring Boot**.

---

| ID | Name | Detection Pattern | Purpose | Spring Boot Target |
|---|---|---|---|---|
| **CS-010** | SessionBean (Stateless) | `implements javax.ejb.SessionBean` + `<session-type>Stateless` | Stateless business logic bean | `@Service` + `@Transactional` |
| **CS-011** | SessionBean (Stateful) | Same + `<session-type>Stateful` or `ejbActivate/ejbPassivate` | Holds conversational state | `@Service` + `@SessionScope` or external StateHolder |
| **CS-012** | EntityBean (CMP) | `implements javax.ejb.EntityBean` + CMP mappings | Container-managed persistence | `@Entity` + Spring Data Repository |
| **CS-013** | EntityBean (BMP) | Manual JDBC in entity bean | Bean-managed persistence | `@Entity` + DAO/Repository extracted logic |
| **CS-014** | MessageDrivenBean (MDB) | `implements javax.ejb.MessageDrivenBean` or `onMessage()` | Asynchronous consumer | `@JmsListener` + `@EnableJms` |
| **CS-015** | Home Interface | `extends javax.ejb.EJBHome/EJBLocalHome` | Factory interface | Removed, replaced by DI |
| **CS-016** | Remote Interface | `extends javax.ejb.EJBObject` | RMI boundary | REST/gRPC client |
| **CS-017** | Local Interface | `extends javax.ejb.EJBLocalObject` | Local business API | Collapsed to interface |
| **CS-018** | Primary Key Class | `implements Serializable`, used in ejb-jar.xml | PK for EntityBean | `@Embeddable` or ID type |
| **CS-019** | ServiceLocator / Factory | Class uses JNDI lookups | Lookup helper | Eliminated / DI injection |
| **CS-020** | Servlet | `extends javax.servlet.http.HttpServlet` | Web entry point | `@RestController` |
| **CS-021** | Filter / Listener | Implements `javax.servlet.Filter` / `Listener` | Cross-cutting concern | `@Component` + `@WebFilter`/`@EventListener` |
| **CS-022** | JSF / Struts Action | Extends `Action` / `BackingBean` | MVC controller | `@Controller` |
| **CS-023** | Tag / JSP Helper | Extends `TagSupport` | View helper | Removed / Thymeleaf fragment |
| **CS-024** | Form Bean / DTO | POJO under dto/form packages | Payload data | `@Data` class or record |
| **CS-030** | DAO / Repository | Ends with DAO, JDBC calls | Data access abstraction | `@Repository` |
| **CS-031** | JPA Entity | `@Entity` already | Modern persistence | Keep as is |
| **CS-032** | Transaction Script | Uses `UserTransaction` / `SessionContext.getUserTransaction()` | Manual tx | `@Service` + `@Transactional` |
| **CS-033** | Utility / Helper | Static methods only | Shared util | `@Component` or static util |
| **CS-034** | Legacy ORM Helper | Vendor CMP APIs | Persistence glue | Remove / replace |
| **CS-040** | TimerBean | Uses `javax.ejb.TimerService` | Scheduler | `@Scheduled` + `@EnableScheduling` |
| **CS-041** | Job / Batch Bean | Invoked by timer/scheduler | Job logic | `@Component` + `@Scheduled` |
| **CS-042** | Listener / Observer | JMS or async listener | Event consumer | `@JmsListener` / `@EventListener` |
| **CS-050** | SecurityFacade / Auth Bean | Uses `SessionContext.getCallerPrincipal()` | Auth logic | `@Service` + Spring Security |
| **CS-051** | Role-based Service | `<method-permission>` in XML | Role enforcement | `@PreAuthorize/@RolesAllowed` |
| **CS-052** | ContextHolder / ThreadLocal | ThreadLocal fields | Context leak risk | Replace with scoped bean |
| **CS-060** | FactoryBean / Provider | Manually provides instances | Factory pattern | Spring `@Bean` config |
| **CS-061** | Cache / Singleton | Static mutable or DCL pattern | Global mutable state | Spring Cache / bean |
| **CS-062** | Interceptor / AOP Helper | Vendor interceptors | Cross-cutting | `@Aspect` |
| **CS-063** | Configuration / Constants | Constant-only class | Config extraction | Move to `application.yml` |
| **CS-064** | Legacy Test Stub | Test-only class | No migration | Spring Boot Test |
| **CS-070** | Mutable Service | Non-final instance fields in service | Thread-unsafe | Refactor R-810 |
| **CS-071** | StateHolder / Stateful Logic | Field write→read across methods | Conversational state | Externalize R-800 |
| **CS-072** | ThreadLocal User | ThreadLocal usage | Thread leakage | Scoped bean |
| **CS-073** | Synchronized Singleton | Synchronized static mutable | Lock contention | Replace with DI bean |

---

## Relationships and Usage
- **Detectors** (I-xxxx) identify raw facts → you infer stereotypes.  
- **Stereotypes** → drive migration recipes:
  - CS-010 → R-110 / R-120
  - CS-011 → R-800
  - CS-012/013 → R-200–R-230
  - CS-014 → R-300
  - CS-070+071 → R-810
  - CS-061+073 → R-830
- Tag results in `manifest.json` for each type.

---

## Recommended Technology
- **binary_with_asm** for bytecode-level detection of implements/extends.  
- **java_parsing** (OpenRewrite/JavaParser) for annotations, method patterns, naming.  
- **GenAI (Bedrock)** optional for heuristic classification and documentation (e.g., "suggest stereotype for unknown class based on methods/comments").

""").strip()

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

path
