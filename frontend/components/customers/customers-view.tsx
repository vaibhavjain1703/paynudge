"use client";

import { FileUp, Pencil, Plus, Search, Trash2, Users } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { CustomerDialog } from "@/components/customers/customer-dialog";
import { DeleteCustomerDialog } from "@/components/customers/delete-customer-dialog";
import { EmptyState, ErrorState } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { type Customer, formatPhone, PAGE_SIZE, useCustomers } from "@/lib/customers";
import { easeOut, useSafeTransition } from "@/lib/motion";

const MotionRow = motion.create(TableRow);

export function CustomersView() {
  const [search, setSearch] = useState("");
  const query = useDebounced(search.trim(), 300);
  // The page belongs to one search: a new search starts again from the first page.
  const [paging, setPaging] = useState({ query: "", page: 0 });
  const page = paging.query === query ? paging.page : 0;
  const goToPage = (next: number) => setPaging({ query, page: next });
  const customers = useCustomers(query, page);
  const [editing, setEditing] = useState<Customer | "new" | null>(null);
  const [deleting, setDeleting] = useState<Customer | null>(null);

  const data = customers.data;
  const showEmptyBook = data && data.totalItems === 0 && !query;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Customers</h1>
          <p className="text-sm text-muted-foreground">Everyone who owes you money, and how to reach them.</p>
        </div>
        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/import">
              <FileUp aria-hidden="true" />
              Import CSV
            </Link>
          </Button>
          <Button onClick={() => setEditing("new")}>
            <Plus aria-hidden="true" />
            Add customer
          </Button>
        </div>
      </div>

      {!showEmptyBook && (
        <div>
          <div className="relative max-w-sm">
            <Search
              className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search name, email or phone"
              aria-label="Search customers"
              className="pl-8"
            />
          </div>
        </div>
      )}

      <div>
        {customers.isPending ? (
          <TableSkeleton />
        ) : customers.isError ? (
          <ErrorState title="Couldn't load your customers" error={customers.error} onRetry={() => customers.refetch()} />
        ) : showEmptyBook ? (
          <EmptyState
            icon={Users}
            title="No customers yet"
            description="Add them one at a time, or import a spreadsheet of everyone who owes you."
            action={
              <div className="flex gap-2">
                <Button asChild variant="outline">
                  <Link href="/import">Import CSV</Link>
                </Button>
                <Button onClick={() => setEditing("new")}>Add customer</Button>
              </div>
            }
          />
        ) : customers.data.totalItems === 0 ? (
          <EmptyState icon={Search} title="No matches" description={`No customer matches "${query}".`} />
        ) : (
          <CustomerTable
            customers={customers.data.items}
            loading={customers.isPlaceholderData}
            onEdit={setEditing}
            onDelete={setDeleting}
          />
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between gap-4 text-sm text-muted-foreground">
          <p className="tabular-nums">
            {data.page * PAGE_SIZE + 1}–{data.page * PAGE_SIZE + data.items.length} of {data.totalItems}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => goToPage(page - 1)}>
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => goToPage(page + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      <CustomerDialog
        customer={editing === "new" ? null : editing}
        open={editing !== null}
        onOpenChange={(open) => !open && setEditing(null)}
      />
      <DeleteCustomerDialog customer={deleting} onOpenChange={(open) => !open && setDeleting(null)} />
    </div>
  );
}

function CustomerTable({
  customers,
  loading,
  onEdit,
  onDelete,
}: {
  customers: Customer[];
  loading: boolean;
  onEdit: (customer: Customer) => void;
  onDelete: (customer: Customer) => void;
}) {
  const transition = useSafeTransition({ duration: 0.2, ease: easeOut });
  return (
    <div
      className="overflow-hidden rounded-xl border transition-opacity duration-200 data-[loading=true]:opacity-60"
      data-loading={loading}
      aria-busy={loading}
    >
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead>Name</TableHead>
            <TableHead className="hidden sm:table-cell">Email</TableHead>
            <TableHead className="hidden md:table-cell">Phone</TableHead>
            <TableHead className="w-24 text-right">
              <span className="sr-only">Actions</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <AnimatePresence initial={false}>
            {customers.map((customer, index) => (
              <MotionRow
                key={customer.id}
                layout="position"
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0, transition: { ...transition, delay: Math.min(index, 10) * 0.02 } }}
                exit={{ opacity: 0, transition }}
              >
                <TableCell className="max-w-0 sm:max-w-none">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium">{customer.name}</span>
                    {customer.optedOut && <Badge variant="secondary">Opted out</Badge>}
                  </div>
                  <div className="truncate text-xs text-muted-foreground sm:hidden">
                    {customer.email ?? (customer.phone && formatPhone(customer.phone))}
                  </div>
                </TableCell>
                <TableCell className="hidden text-muted-foreground sm:table-cell">{customer.email ?? "—"}</TableCell>
                <TableCell className="hidden font-mono text-xs text-muted-foreground md:table-cell">
                  {customer.phone ? formatPhone(customer.phone) : "—"}
                </TableCell>
                <TableCell className="text-right">
                  <Button variant="ghost" size="icon-sm" aria-label={`Edit ${customer.name}`} onClick={() => onEdit(customer)}>
                    <Pencil aria-hidden="true" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Delete ${customer.name}`}
                    onClick={() => onDelete(customer)}
                    className="text-muted-foreground hover:text-destructive"
                  >
                    <Trash2 aria-hidden="true" />
                  </Button>
                </TableCell>
              </MotionRow>
            ))}
          </AnimatePresence>
        </TableBody>
      </Table>
    </div>
  );
}

function TableSkeleton() {
  return (
    <div className="space-y-px overflow-hidden rounded-xl border" aria-busy="true" aria-label="Loading customers">
      {Array.from({ length: 6 }, (_, index) => (
        <div key={index} className="flex items-center gap-4 px-3 py-3">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="hidden h-4 w-48 sm:block" />
          <Skeleton className="ml-auto h-4 w-12" />
        </div>
      ))}
    </div>
  );
}

function useDebounced<T>(value: T, delayMs: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
