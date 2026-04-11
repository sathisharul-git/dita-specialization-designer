<?xml version="1.0" encoding="UTF-8"?>
<!--
  Built-in DITA → HTML transformation template.
  Handles common DITA topic structures: title, shortdesc, body,
  p, ul/li, ol/li, note, table, codeblock, pre, b, i, tt.
  Extend or replace with a full DITA-OT stylesheet for production use.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

  <xsl:output method="html" version="5.0" encoding="UTF-8" indent="yes"/>

  <!-- ── Parameters ────────────────────────────────────────────────── -->
  <xsl:param name="css-url" select="''" as="xs:string"/>
  <xsl:param name="doc-title" select="'DITA Output'" as="xs:string"/>

  <!-- ── Root: wrap in HTML page ───────────────────────────────────── -->
  <xsl:template match="/">
    <html lang="en">
      <head>
        <meta charset="UTF-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <title>
          <xsl:value-of select="(//*[local-name()='title'])[1]"/>
        </title>
        <xsl:if test="$css-url != ''">
          <link rel="stylesheet" href="{$css-url}"/>
        </xsl:if>
        <style>
          body {{ font-family: 'Segoe UI', Arial, sans-serif; max-width: 860px;
                  margin: 2em auto; padding: 0 1em; color: #333; }}
          h1   {{ color: #004080; border-bottom: 2px solid #004080; padding-bottom: .3em; }}
          h2   {{ color: #005a9c; }}
          .shortdesc {{ font-style: italic; color: #555; margin-bottom: 1.2em; }}
          .note  {{ border-left: 4px solid #2980b9; padding: .5em 1em;
                    background: #eaf4fb; margin: 1em 0; }}
          pre, .codeblock {{ background: #1e1e1e; color: #d4d4d4;
                              padding: 1em; overflow: auto; border-radius: 4px; }}
          table {{ border-collapse: collapse; width: 100%; }}
          th, td {{ border: 1px solid #ccc; padding: .4em .8em; text-align: left; }}
          th {{ background: #f0f0f0; font-weight: bold; }}
        </style>
      </head>
      <body>
        <xsl:apply-templates/>
      </body>
    </html>
  </xsl:template>

  <!-- ── Topic / concept / task / reference ─────────────────────────── -->
  <xsl:template match="*[local-name()='topic' or local-name()='concept'
                          or local-name()='task' or local-name()='reference']">
    <article>
      <xsl:apply-templates/>
    </article>
  </xsl:template>

  <!-- ── Title ──────────────────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='title']">
    <xsl:variable name="depth" select="count(ancestor::*[local-name()='topic'
                                            or local-name()='section'])"/>
    <xsl:element name="h{min($depth + 1, 6)}">
      <xsl:apply-templates/>
    </xsl:element>
  </xsl:template>

  <!-- ── Short description ──────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='shortdesc']">
    <p class="shortdesc"><xsl:apply-templates/></p>
  </xsl:template>

  <!-- ── Body wrappers (pass-through) ──────────────────────────────── -->
  <xsl:template match="*[local-name()='body' or local-name()='conbody'
                          or local-name()='taskbody' or local-name()='refbody'
                          or local-name()='section']">
    <xsl:apply-templates/>
  </xsl:template>

  <!-- ── Paragraph ─────────────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='p']">
    <p><xsl:apply-templates/></p>
  </xsl:template>

  <!-- ── Lists ─────────────────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='ul']">
    <ul><xsl:apply-templates/></ul>
  </xsl:template>
  <xsl:template match="*[local-name()='ol']">
    <ol><xsl:apply-templates/></ol>
  </xsl:template>
  <xsl:template match="*[local-name()='li']">
    <li><xsl:apply-templates/></li>
  </xsl:template>

  <!-- ── Note ──────────────────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='note']">
    <div class="note">
      <strong>
        <xsl:choose>
          <xsl:when test="@type='warning'">Warning: </xsl:when>
          <xsl:when test="@type='caution'">Caution: </xsl:when>
          <xsl:when test="@type='tip'">Tip: </xsl:when>
          <xsl:otherwise>Note: </xsl:otherwise>
        </xsl:choose>
      </strong>
      <xsl:apply-templates/>
    </div>
  </xsl:template>

  <!-- ── Code blocks ───────────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='codeblock' or local-name()='pre']">
    <pre><code><xsl:apply-templates/></code></pre>
  </xsl:template>
  <xsl:template match="*[local-name()='codeph' or local-name()='tt']">
    <code><xsl:apply-templates/></code>
  </xsl:template>

  <!-- ── Inline formatting ─────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='b' or local-name()='ph'][@outputclass='bold']">
    <strong><xsl:apply-templates/></strong>
  </xsl:template>
  <xsl:template match="*[local-name()='i']">
    <em><xsl:apply-templates/></em>
  </xsl:template>
  <xsl:template match="*[local-name()='u']">
    <u><xsl:apply-templates/></u>
  </xsl:template>
  <xsl:template match="*[local-name()='xref']">
    <a href="{@href}"><xsl:apply-templates/></a>
  </xsl:template>

  <!-- ── Simple table ──────────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='simpletable']">
    <table>
      <xsl:apply-templates/>
    </table>
  </xsl:template>
  <xsl:template match="*[local-name()='sthead']">
    <thead><tr>
      <xsl:for-each select="*[local-name()='stentry']">
        <th><xsl:apply-templates/></th>
      </xsl:for-each>
    </tr></thead>
  </xsl:template>
  <xsl:template match="*[local-name()='strow']">
    <tr>
      <xsl:for-each select="*[local-name()='stentry']">
        <td><xsl:apply-templates/></td>
      </xsl:for-each>
    </tr>
  </xsl:template>

  <!-- ── Image ─────────────────────────────────────────────────────── -->
  <xsl:template match="*[local-name()='image']">
    <img src="{@href}" alt="{*[local-name()='alt']}"/>
  </xsl:template>

  <!-- ── Default: recurse into unknown elements ─────────────────────── -->
  <xsl:template match="*">
    <xsl:apply-templates/>
  </xsl:template>

</xsl:stylesheet>
