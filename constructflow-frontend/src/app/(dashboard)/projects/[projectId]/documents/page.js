"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Download, FileText, History, Upload } from "lucide-react";
import { toast } from "sonner";
import { Card, EmptyState, ErrorState, LoadingSkeleton } from "@/components/feedback/States";
import { Pagination } from "@/components/ui/DataTable";
import { FilterBar, SearchField } from "@/components/ui/FilterBar";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { FormField, Input, Select } from "@/components/forms/Fields";
import { useAuth } from "@/features/auth/AuthProvider";
import { useProject } from "@/features/projects/api";
import { api, downloadFile, errorMessage } from "@/lib/api/client";
import { useApiMutation, usePagedQuery } from "@/lib/api/hooks";
import { DOCUMENT_CATEGORIES } from "@/lib/constants/status";
import { formatDateTime, humanize } from "@/lib/utils/format";

const MAX_BYTES = 25 * 1024 * 1024;
const ACCEPT = ".pdf,.png,.jpg,.jpeg,.docx,.xlsx";

function formatSize(bytes) {
  return bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export default function DocumentsPage() {
  const { projectId } = useParams();
  const { can, me } = useAuth();
  const { data: project } = useProject(projectId);
  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");
  const [uploadFor, setUploadFor] = useState(null); // {} = new document, document = new version
  const [historyFor, setHistoryFor] = useState(null);
  const list = usePagedQuery(["documents", projectId], `/projects/${projectId}/documents`, { filters: { q, category }, initialSort: "updatedAt,desc" });
  const canUpload = can("DOCUMENT_UPLOAD") && (project?.myProjectRole || me.role === "ADMIN") && !["COMPLETED", "CANCELLED"].includes(project?.status);

  const download = async (doc, versionNo) => {
    try {
      await downloadFile(`/documents/${doc.id}/download${versionNo ? `?versionNo=${versionNo}` : ""}`, doc.title);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  return (
    <Card title="Documents" description="Drawings, contracts, permits and photos. Every upload keeps earlier revisions."
      actions={canUpload && <Button size="sm" onClick={() => setUploadFor({})}><Upload className="h-4 w-4" aria-hidden="true" /> Upload</Button>}>
      <FilterBar>
        <SearchField value={q} onChange={(v) => { setQ(v); list.setPage(0); }} placeholder="Search titles" />
        <Select aria-label="Category" className="w-full sm:w-44" value={category} onChange={(e) => { setCategory(e.target.value); list.setPage(0); }} options={DOCUMENT_CATEGORIES} placeholder="All categories" />
      </FilterBar>
      {list.error ? <ErrorState error={list.error} onRetry={list.refetch} /> : list.isPending ? <LoadingSkeleton /> : list.rows.length === 0 ? (
        <EmptyState icon={FileText} title="No documents" description="Upload PDF, image, Word or Excel files up to 25 MB." />
      ) : (
        <ul className="divide-y divide-border">
          {list.rows.map((doc) => (
            <li key={doc.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium">{doc.title}</p>
                  <Badge>{humanize(doc.category)}</Badge>
                  <Badge tone="info">v{doc.currentVersionNo}</Badge>
                </div>
                {doc.latest && (
                  <p className="text-xs text-muted">
                    {doc.latest.originalFilename} · {formatSize(doc.latest.sizeBytes)} · {doc.latest.uploadedBy.fullName}, {formatDateTime(doc.latest.uploadedAt)}
                  </p>
                )}
              </div>
              <div className="flex gap-1">
                <Button size="sm" variant="ghost" onClick={() => download(doc)} aria-label={`Download ${doc.title}`}><Download className="h-4 w-4" /></Button>
                <Button size="sm" variant="ghost" onClick={() => setHistoryFor(doc)} aria-label={`Revision history of ${doc.title}`}><History className="h-4 w-4" /></Button>
                {canUpload && <Button size="sm" variant="outline" onClick={() => setUploadFor(doc)}>New version</Button>}
              </div>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={list.page} size={list.size} totalPages={list.data?.totalPages} totalElements={list.data?.totalElements} onPageChange={list.setPage} />
      {uploadFor && <UploadDialog projectId={projectId} document={uploadFor.id ? uploadFor : null} onClose={() => setUploadFor(null)} />}
      <HistoryDialog document={historyFor} onClose={() => setHistoryFor(null)} onDownload={download} />
    </Card>
  );
}

function UploadDialog({ projectId, document, onClose }) {
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("DRAWING");
  const [changeNote, setChangeNote] = useState("");
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState(null);
  const upload = useApiMutation(() => {
    const form = new FormData();
    form.append("file", file);
    if (changeNote) form.append("changeNote", changeNote);
    if (document) {
      form.append("version", String(document.version));
      return api.post(`/documents/${document.id}/versions`, form);
    }
    form.append("title", title);
    form.append("category", category);
    return api.post(`/projects/${projectId}/documents`, form);
  }, { invalidate: [["documents"]], success: document ? "New version uploaded" : "Document uploaded", onSuccess: onClose });

  const pick = (selected) => {
    setFileError(null);
    if (selected && selected.size > MAX_BYTES) {
      setFileError("The file is larger than 25 MB.");
      setFile(null);
      return;
    }
    setFile(selected ?? null);
  };

  return (
    <Dialog open onClose={onClose} title={document ? `New version of ${document.title}` : "Upload document"}
      description="Allowed: PDF, PNG, JPEG, DOCX, XLSX up to 25 MB. The file type is verified on the server."
      footer={<><Button variant="outline" onClick={onClose}>Cancel</Button>
        <Button loading={upload.isPending} disabled={!file || (!document && !title.trim())} onClick={() => upload.mutate()}>Upload</Button></>}>
      <div className="space-y-4">
        {!document && (
          <>
            <FormField label="Title" required>{(a11y) => <Input {...a11y} value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />}</FormField>
            <FormField label="Category" required>{(a11y) => <Select {...a11y} value={category} onChange={(e) => setCategory(e.target.value)} options={DOCUMENT_CATEGORIES} />}</FormField>
          </>
        )}
        <FormField label="File" required error={fileError}>
          {(a11y) => <input {...a11y} type="file" accept={ACCEPT} onChange={(e) => pick(e.target.files?.[0])}
            className="block w-full text-sm file:mr-3 file:rounded-md file:border-0 file:bg-neutral-soft file:px-3 file:py-2 file:text-sm file:font-medium" />}
        </FormField>
        <FormField label="Change note">{(a11y) => <Input {...a11y} value={changeNote} maxLength={500} placeholder="e.g. Rev B – column sizes updated" onChange={(e) => setChangeNote(e.target.value)} />}</FormField>
      </div>
    </Dialog>
  );
}

function HistoryDialog({ document, onClose, onDownload }) {
  const documentId = document?.id;
  const { data, isPending, error } = useQuery({
    queryKey: ["documents", "versions", documentId],
    queryFn: () => api.get(`/documents/${documentId}/versions`),
    enabled: Boolean(documentId),
  });
  return (
    <Dialog open={Boolean(document)} onClose={onClose} title={`Revisions · ${document?.title ?? ""}`}>
      {error ? <ErrorState error={error} /> : isPending ? <LoadingSkeleton rows={3} /> : (
        <ol className="space-y-3">
          {data.map((v) => (
            <li key={v.id} className="flex items-start justify-between gap-3 text-sm">
              <div>
                <p className="font-medium">Version {v.versionNo} · {v.originalFilename}</p>
                <p className="text-xs text-muted">{v.uploadedBy.fullName}, {formatDateTime(v.uploadedAt)} · {formatSize(v.sizeBytes)}</p>
                {v.changeNote && <p className="text-xs text-muted">“{v.changeNote}”</p>}
                <p className="font-mono text-[11px] text-muted" title="SHA-256 checksum">sha256 {v.sha256.slice(0, 16)}…</p>
              </div>
              <Button size="sm" variant="ghost" onClick={() => onDownload(document, v.versionNo)} aria-label={`Download version ${v.versionNo}`}>
                <Download className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ol>
      )}
    </Dialog>
  );
}
