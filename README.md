## Scala githubRank challenge repository

This repository contains code wrote for [Scalac](scalac.io) recruitment challenge.

Usage:
- Launch the app from Server.scala class
- Go to http://localhost:8080/org/{org_name}/contributors (where org_name is name of organization you look for)
- Output will show JSON respond with contributors of given organization's all repositories (sorted by number of contributions)

Note:
- To omit the GitHub Api rate limit restriction set the environment variable GH_TOKEN as "token AUTH_TOKEN"