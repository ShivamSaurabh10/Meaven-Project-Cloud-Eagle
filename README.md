# Meaven-Project-Cloud-Eagle

# GitHub Organization Access Report Service

## Overview

This project is a backend service that connects to the GitHub API and generates a report showing **which users have access to which repositories within a given GitHub organization**.

The service retrieves repositories from a GitHub organization, determines which users have access to each repository, aggregates the data, and exposes an API endpoint that returns the access report in **JSON format**.

The application is designed to handle organizations with **100+ repositories and 1000+ users** efficiently.

---

# How to Run the Project

## 1. Clone the Repository

```bash
git clone https://github.com/your-username/github-access-report.git
cd github-access-report
```

## 2. Set GitHub Token

Create a **GitHub Personal Access Token** with the required permissions.

Required permissions:

* `repo`
* `read:org`

Set the token as an environment variable:

**Windows**

```bash
set GITHUB_TOKEN=your_token_here
```

**Linux / Mac**

```bash
export GITHUB_TOKEN=your_token_here
```

## 3. Configure Organization Name

Set the GitHub organization name in `application.properties`:

```properties
github.organization=your-org-name
```

## 4. Build the Project

```bash
mvn clean install
```

## 5. Run the Application

```bash
mvn spring-boot:run
```

The server will start on:

```
http://localhost:8080
```

---

# Authentication Configuration

Authentication with GitHub is implemented using a **GitHub Personal Access Token (PAT)**.

### Why Token Authentication?

* More secure than username/password
* Recommended by GitHub
* Works with GitHub REST APIs

### Configuration

The token is stored as an **environment variable**:

```
GITHUB_TOKEN
```

The application reads this token and sends it in API requests using the `Authorization` header:

```
Authorization: Bearer <GITHUB_TOKEN>
```

This allows the service to securely fetch repository and collaborator information from the GitHub API.

---

# How to Call the API Endpoint

Once the application is running, the access report can be retrieved using the following endpoint.

### Endpoint

```
GET /api/access-report
```

### Example Request

```bash
curl http://localhost:8080/api/access-report
```

### Example JSON Response

```json
{
  "users": [
    {
      "username": "user1",
      "repositories": [
        "repo-a",
        "repo-b"
      ]
    },
    {
      "username": "user2",
      "repositories": [
        "repo-a"
      ]
    }
  ]
}
```

The response shows:

* Each **user**
* The **repositories they have access to**

---

# Assumptions and Design Decisions

### 1. GitHub REST API

The service uses the **GitHub REST API** to retrieve:

* Organization repositories
* Repository collaborators

### 2. Aggregation Strategy

Instead of returning repository-based results, the service aggregates data into a **user → repositories mapping** for easier analysis.

### 3. Scalability Considerations

To support organizations with large numbers of repositories and users:

* Pagination is used for GitHub API calls
* API calls are minimized
* Data aggregation is done in-memory efficiently

### 4. Error Handling

The application includes handling for:

* Invalid GitHub tokens
* Organization not found
* API rate limit errors
* Network failures

### 5. Clean Code Structure

Typical structure:

```
controller/
    AccessReportController.java

service/
    GitHubService.java
    AccessReportService.java

client/
    GitHubApiClient.java

model/
    Repository.java
    UserAccessReport.java

config/
    GitHubConfig.java
```

### 6. Security

* No credentials are stored in the repository
* Authentication uses environment variables
* Token is passed securely via request headers

---

# Future Improvements

Possible enhancements:

* Add caching to reduce GitHub API calls
* Support multiple organizations
* Add role/permission levels in the report
* Add authentication for the API itself
* Add unit and integration tests

---

# Technologies Used

* Java
* Spring Boot
* Maven
* GitHub REST API
* Jackson (JSON processing)

---

